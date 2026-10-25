#!/usr/bin/env python3
"""Turns a playtest's latest.log into a one-page Markdown report.

    python3 tools/playtest/report.py logs/latest.log [logs/2026-10-25-1.log.gz ...] [-o report.md]

Reads the `[InfiniteCraft/playtest] {...}` lines the mod writes (see PlaytestLog.java): what was crafted, what
each result does, whether it got used and whether it was kept. Give rotated logs before latest.log to cover a
session across restarts. Standard library only.
"""

import argparse
import gzip
import json
import re
import sys
import time
from collections import OrderedDict

TAG = "[InfiniteCraft/playtest] "

# The mod's abilities, as BehaviorSignature names them; later parts append theirs
ABILITIES = ["shoot", "thrust", "launch", "dash", "aura", "vehicle"]

ANSI = re.compile(r"\x1b\[[0-9;]*m")


def events(lines):
    """The playtest events in the lines, skipping everything else and any tagged line that isn't valid JSON"""
    for line in lines:
        line = ANSI.sub("", line)
        at = line.find(TAG)
        if at < 0:
            continue
        try:
            event = json.loads(line[at + len(TAG):])
        except json.JSONDecodeError:
            continue
        if isinstance(event, dict) and "event" in event and "t" in event:
            yield event


def duration(ms):
    seconds = round(ms / 1000)
    return "%dm %02ds" % (seconds // 60, seconds % 60) if seconds >= 60 else "%ds" % seconds


def offset(ms):
    seconds = round(ms / 1000)
    return "%02d:%02d" % (seconds // 60, seconds % 60)


def cell(text):
    return str(text).replace("\n", " ").replace("|", "\\|")


def path(item):
    return item.split(":", 1)[1] if item.startswith("minecraft:") else item


def wish(inputs):
    """Renamed ingredients (the wishes) first, as "Name" (item), then the rest"""
    named = ['"%s" (%s)' % (i["name"], path(i["item"])) for i in inputs if i.get("name")]
    plain = [path(i["item"]) for i in inputs if not i.get("name")]
    return " + ".join(named + plain)


def abilities_in(signature):
    names = set()
    for token in signature.split(" + "):
        names.add(token.split("(", 1)[0])
    return names


def build_report(lines):
    evs = sorted(events(lines), key=lambda e: e["t"])
    crafts = [e for e in evs if e["event"] == "craft"]
    if not crafts:
        return "# Playtest report\n\nNo crafts in this log.\n"
    failures = [e for e in evs if e["event"] == "craft_failed"]
    start, end = evs[0]["t"], evs[-1]["t"]

    # uses per craft id: kind -> count, in the order kinds first came up
    uses = {}
    for e in evs:
        if e["event"] == "use":
            uses.setdefault(e["id"], OrderedDict()).setdefault(e["kind"], 0)
        elif e["event"] == "uses":
            counts = uses.setdefault(e["id"], OrderedDict())
            for kind, n in e["counts"].items():
                counts[kind] = counts.get(kind, 0) + n
    drops = {}
    for e in evs:
        if e["event"] == "drop":
            drops.setdefault(e["id"], e["t"])
    snapshots = [e for e in evs if e["event"] == "snapshot"]

    out = ["# Playtest report", "", "## Session", ""]
    day = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(start / 1000))
    out.append("- %s to %s (%s)" % (day, time.strftime("%H:%M:%S", time.localtime(end / 1000)), duration(end - start)))
    out.append("- %d crafts, %d failed" % (len(crafts), len(failures)))
    out.append("- cost: $%.2f" % sum(c.get("costUsd", 0) for c in crafts))
    out += ["", "## Crafts", "", "| # | at | wish or recipe | result | does | used | held | kept |", "|---|---|---|---|---|---|---|---|"]

    never_used, dropped_fast, partial = [], [], []
    for n, c in enumerate(crafts, 1):
        name = c.get("name", "?")
        signature = c.get("signature", "")
        cid = c.get("id")
        if cid is None:
            used = held = kept = "—"
        else:
            counts = uses.get(cid, {})
            used = ", ".join("%s ×%d" % (kind, max(count, 1)) for kind, count in counts.items()) or "no"
            if not counts:
                never_used.append("#%d %s" % (n, cell(name)))
            player_snaps = [s for s in snapshots if s.get("player") == c.get("player") and s["t"] >= c["t"]]
            kept_now = cid in player_snaps[-1]["held"] if player_snaps else cid not in drops
            kept = "yes" if kept_now else "no"
            if cid in drops:
                held = "dropped after %s" % duration(drops[cid] - c["t"])
                if drops[cid] - c["t"] < 60_000:
                    dropped_fast.append("#%d %s (after %s)" % (n, cell(name), duration(drops[cid] - c["t"])))
            elif kept_now:
                held = duration(end - c["t"])
            else:
                gone = next(s for s in player_snaps if cid not in s["held"])
                held = "gone by %s" % offset(gone["t"] - start)
        if c.get("verdict", "").startswith("partial"):
            partial.append("#%d %s (%s)" % (n, cell(name), cell(c["verdict"])))
        out.append("| %d | %s | %s | %s | %s | %s | %s | %s |" % (
            n, offset(c["t"] - start), cell(wish(c.get("inputs", []))), cell(name), cell(signature) or "—", used, held, kept))
    out += ["", "*used* counts right-clicks (use), melee hits (hit), putting it on (wear) and vehicle shots (fire). "
            "Plain vanilla results and elements aren't tracked (—).", ""]

    # variety: crafted custom items that do something, by what they do
    by_signature = OrderedDict()
    for c in crafts:
        if c.get("id") and c.get("signature"):
            by_signature.setdefault(c["signature"], []).append(cell(c.get("name", "?")))
    out += ["## Variety", "", "Distinct behaviors: %d of %d" % (len(by_signature), sum(len(v) for v in by_signature.values())), ""]
    repeats = [(s, names) for s, names in by_signature.items() if len(names) > 1]
    if repeats:
        out.append("Same behavior, different names:")
        out += ["- %s: %s" % (s, ", ".join(names)) for s, names in repeats]
        out.append("")
    seen = set()
    for c in crafts:
        seen |= abilities_in(c.get("signature", ""))
    missing = [a for a in ABILITIES if a not in seen]
    out += ["Abilities that never came up: %s" % (", ".join(missing) if missing else "none"), ""]

    flags = []
    if never_used:
        flags.append("- Never used: " + ", ".join(never_used))
    if dropped_fast:
        flags.append("- Dropped within a minute: " + ", ".join(dropped_fast))
    if partial:
        flags.append("- Minecraft dropped some of its parts: " + ", ".join(partial))
    for f in failures:
        flags.append("- Failed: %s %s: %s" % (offset(f["t"] - start), cell(wish(f.get("inputs", []))), cell(f.get("error", "?"))))
    out += ["## Flags", ""] + (flags or ["- none"])
    return "\n".join(out) + "\n"


def read(paths):
    for p in paths:
        opener = gzip.open if p.endswith(".gz") else open
        with opener(p, "rt", encoding="utf-8", errors="replace") as f:
            yield from f


def main():
    parser = argparse.ArgumentParser(description="Turn a playtest log into a Markdown report")
    parser.add_argument("logs", nargs="+", help="latest.log, and any rotated *.log.gz before it")
    parser.add_argument("-o", "--out", help="write the report here instead of printing it")
    args = parser.parse_args()
    text = build_report(read(args.logs))
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(text)
    else:
        sys.stdout.write(text)


if __name__ == "__main__":
    main()
