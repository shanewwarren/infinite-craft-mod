package com.infinite_craft.ability;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

/**
 * Record codecs that report fields they don't know. Claude sometimes writes one (a summon's gear.chest before it
 * existed), and a plain record codec drops it without a word, so the craft was graded clean. Here the item still
 * parses with everything it did know, but as a partial result whose error names the field: the craft's verdict
 * becomes "partial: unknown field ..." and the eval grades it usable
 */
public final class Strict {

	private Strict() {}

	public static <A> Codec<A> of(MapCodec<A> fields) {
		return map(fields, null).codec();
	}

	/** dispatchKey is the field a dispatch codec reads before this one (a consume effect's "type"), or null */
	public static <A> MapCodec<A> map(MapCodec<A> fields, String dispatchKey) {
		return new MapCodec<>() {
			@Override
			public <T> Stream<T> keys(DynamicOps<T> ops) {
				return fields.keys(ops);
			}

			@Override
			public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
				DataResult<A> result = fields.decode(ops, input);
				Set<String> known = fields.keys(ops).map(k -> ops.getStringValue(k).result().orElse("")).collect(Collectors.toSet());
				List<String> unknown = input.entries().map(e -> ops.getStringValue(e.getFirst()).result().orElse("?"))
					.filter(k -> !known.contains(k) && !k.equals(dispatchKey)).toList();
				if (unknown.isEmpty()) return result;
				return result.flatMap(decoded -> DataResult.error(() -> "unknown field " + String.join(", ", unknown), decoded));
			}

			@Override
			public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
				return fields.encode(input, ops, prefix);
			}
		};
	}
}
