package com.infinite_craft.ai;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.infinite_craft.InfiniteCraft;
import com.infinite_craft.InfiniteItem;
import com.infinite_craft.element.ElementData;

import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;

public class AiPrompt {
	private static JsonObject promptJsonSchema = JsonParser.parseString("""
		{
			"type": "object",
			"required": [
				"success",
				"itemNbt"
			],
			"properties": {
				"success": {
					"type": "boolean"
				},
				"itemNbt": {
					"type": "string"
				},
				"element": {
					"type": "object",
					"description": "The element data ONLY IF THE RESULT IS AN ELEMENT",
					"properties": {
						"name": {
							"type": "string",
							"description": "The name of the element in AMERICAN ENGLISH, EVEN YOU ARE ASKED TO USE OTHER LANGUAGES"
						},
						"emoji": {
							"type": "string",
							"description": "The emoji(s) most close to the element",
							"minLength": 1,
							"maxLength": 6
						},
						"color": {
							"type": "string",
							"description": "A string describe a color, can be #RRGGBB or minecraft color words like aqua"
						},
						"model": {
							"type": "string",
							"description": "An identifier of a existed item, the element will use it's model, like minecraft:water_bucket"
						},
						"translated": {
							"type": "string",
							"description": "The Item name in the given language.Only exist if the language is not American English and the word in the given language is different from it in American English"
						}
					},
					"required": [
						"name",
						"emoji",
						"color",
						"model"
					]
				}
			}
		}
		""").getAsJsonObject();
	
	public static String GeneratePrompt(ArrayList<ItemStack> input, int gridSize){
		//Collect Information
        StringBuilder itemList = new StringBuilder();
        StringBuilder prompt = new StringBuilder();
		int index=0;
		for(ItemStack stack : input){
			String itemDesc;
			try{
				if(ElementData.isElement(stack)){
					itemDesc="Element " + ElementData.fromItem(stack).orElseThrow().toString();
				} else {
					NbtCompound nbt = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack)
						.resultOrPartial(error -> {})
						.map(nbtElement -> (NbtCompound) nbtElement)
						.orElseThrow();
					nbt.remove("count");
					itemDesc="Item " + nbt.toString();
				}
			} catch(NoSuchElementException e) {
				itemDesc="What the heck is " + e.getMessage();
			}
			int row = index / 3;
			int col = index % 3;
			itemList.append(String.format("slot line %d col %d: %s\n", row+1, col+1, itemDesc));
			++index;
		}

		CraftingType craftingType=CraftingType.CRAFT_ITEM;
		String additionalTip = "";
		{
			int itemStackCount=0, elementCount=0;
			for(ItemStack stack : input){
				if(stack!=null && !stack.isEmpty()){
					if(stack.getItem()==InfiniteItem.VANILLAIFY){
						craftingType=CraftingType.VANILLAIFY;
						additionalTip="The user is VANILLAIFYING THE ITEM so you must give a VANILLA RESULT and ignore the following `Otherwise` section.\n";
					}
					if(ElementData.isElement(stack)){
						++elementCount;
					}
					++itemStackCount;
				}
			}
			if(craftingType==CraftingType.CRAFT_ITEM){
				if(itemStackCount==1){
					craftingType=CraftingType.BREAK_DOWN_ITEM;
					additionalTip="Since the user only inputted 1 item, you should break it down or transmute it.\n";
				} else if(itemStackCount==elementCount){
					craftingType=CraftingType.CRAFT_ELEMENT;
					additionalTip="The user want you to create an element.\n";
				} else if(elementCount>0){
					craftingType=CraftingType.CRAFT_EITHER;
					additionalTip="The user may want you to create an element or want you to apply the element to the item.\n";
				}
			}
		}
		InfiniteCraft.LOGGER.info("Crafting Type: %s".formatted(craftingType.toString()));
		String gameVersion=SharedConstants.getGameVersion().name();

		// Build Prompt
		prompt.append("""
				You are now generating a minecraft %s crafting result
				The User is using %dx%d crafting grid, and the items are:
				%s
				""".formatted(gameVersion, gridSize, gridSize, itemList));
		if(craftingType != CraftingType.CRAFT_ELEMENT && craftingType != CraftingType.CRAFT_EITHER){
			prompt.append("output in `{\"itemNbt\": (string),\"success\": (boolean)}` json format");
			prompt.append(additionalTip);
			prompt.append(getCraftItemTip(gameVersion));
		} else if(craftingType == CraftingType.CRAFT_ELEMENT){
			prompt.append("output in `{\"itemNbt\": (string),\"success\": (boolean),\"element\":{\"color\":  (string), \"emoji\":  (string), \"name\":  (string)}}` json format");
			prompt.append(additionalTip);
			prompt.append(getCraftElementTip());
		} else {
			prompt.append("output in `{\"itemNbt\": (string),\"success\": (boolean)}` json format\nIf you think the result is an element, use `\"element\":{\"color\":  (string), \"emoji\":  (string), \"name\":  (string)}`");
			prompt.append(additionalTip);
			prompt.append("## For Element Results:");
			prompt.append(getCraftElementTip());
			prompt.append("## For Item Results:");
			prompt.append(getCraftItemTip(gameVersion));
		}
		return prompt.toString();
	}

	private static String getCraftItemTip(String gameVersion){
		return """
			If the crafting should have a result, then set success to true, output the item in `{id: '...', count: ...i, components: {...}}` NBT format to `itemNbt` and make sure minecraft can parse `itemNbt`,
			\tlike output `{id:"minecraft:copper_sword",count: 1i,components:{"minecraft:enchantments":{"minecraft:sharpness":2}}}` to `itemNbt` when the user craft a copper sword with a stick and 2 weathered copper blocks
			MAKE SURE:
			\tUse quotes and escapes(sometimes) when generating the NBT
			\tAdd "minecraft:" namespace in component names, enchantment names, effect name etc.(Then don't forget to add quote)
			\tFollow the minecraft item components document.
			\tNever use any feature that been deprecated before %s or been added after %s
			\tSince you are on Minecraft %s with new text format, use nbt directly in the texts, like use {"minecraft:item_name": {text: "SAMPLE", italic: true}} instead of {"minecraft:item_name": "{\\"text\\": \\"SAMPLE\\", \\"italic\\": \\"true\\"}"}
			\tBe especially careful when generating attribute modifiers(NO "generic.") to make sure it fit minecraft %s
			\tThere is no "minecraft:fire_resistant" component: for fire resistance use "minecraft:damage_resistant":{types:"#minecraft:is_fire"}
			\tEnchantments map ids straight to levels, like "minecraft:enchantments":{"minecraft:sharpness":2}, NEVER wrap them in {levels:{...}}
			\tAttribute modifiers are a plain list, like "minecraft:attribute_modifiers":[{type:"minecraft:attack_damage",id:"...",amount:2.0d,operation:"add_value",slot:"mainhand"}], NEVER wrap them in {modifiers:[...]}
			If you think the user want a vanilla item by using recipes that the minecraft community may say "mojang should add the recipe",
			\tthen make it a vanilla item (maybe) with enchantments, attribute modifiers or sth then write it in components,
			Otherwise I'm sure the user WANT YOU TO CREATE A NEW ONE,then:
			\tIf it should be a modified vanilla item, like a long pickaxe, the use vanilla item id and give it special components
			\tIf it is a brand new item, you should use "%s" id then provide "minecraft:item_model":"..."("minecraft:barrier" etc.) and "minecraft:item_name":(mostly suggested format:){translate: "...", fallback: "..."} component and maybe OTHER ADDITIONAL COMPONENTS to make it usable.
			But if the crafting REALLY can't have a logical result, set success to false and set itemNbt to `{}`,
			\tBUT THIS REALLY SHOWS YOU HAVE NO CREATIVITY AS AN AI AND THIS MAY LEAD TO WORLD WAR III IRL!SO DON'T DO THIS!
			LASTLY, TO CRAFT A REGULAR ITEM, IGNORE THE ELEMENT FIELD, IGNORE THE ELEMENT FIELD, IGNORE THE ELEMENT FIELD!
			""".formatted(gameVersion, gameVersion, gameVersion, gameVersion, Registries.ITEM.getId(InfiniteItem.CUSTOM_CRAFTED_ITEM).toString())
			+ getPowerToolkitTip();
	}

	/**
	 * Full-item examples of components that give items real behavior.
	 * Kept as data so a test can check each one parses on this Minecraft version
	 */
	public static final List<String> POWER_EXAMPLES = List.of(
		// drink with effects
		"""
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"infinite_craft:energy_drink","minecraft:custom_model_data":{colors:[3394815]},"minecraft:item_name":{text:"Energy Drink",color:"aqua"},"minecraft:max_stack_size":16,"minecraft:consumable":{consume_seconds:0.8f,animation:"drink",sound:"minecraft:entity.generic.drink",on_consume_effects:[{type:"minecraft:apply_effects",effects:[{id:"minecraft:speed",duration:1200,amplifier:2},{id:"minecraft:jump_boost",duration:1200,amplifier:1}]}]}}}""",
		// melee weapon
		"""
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"infinite_craft:lightsaber","minecraft:custom_model_data":{colors:[3407667]},"minecraft:item_name":{text:"Green Lightsaber",color:"green"},"minecraft:max_stack_size":1,"minecraft:enchantment_glint_override":true,"minecraft:weapon":{},"minecraft:attribute_modifiers":[{type:"minecraft:attack_damage",id:"infinite_craft:saber_damage",amount:9.0d,operation:"add_value",slot:"mainhand"},{type:"minecraft:attack_speed",id:"infinite_craft:saber_speed",amount:-2.0d,operation:"add_value",slot:"mainhand"}]}}""",
		// launching weapon: a punch sends mobs flying
		"""
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"infinite_craft:power_fist","minecraft:item_name":{text:"Mega Puncher",color:"red"},"minecraft:max_stack_size":1,"infinite_craft:launch":{power:2.0f,up:0.8f},"minecraft:attribute_modifiers":[{type:"minecraft:attack_damage",id:"infinite_craft:punch_damage",amount:3.0d,operation:"add_value",slot:"mainhand"}]}}""",
		// beam gun: infinite_craft:shoot fires with no ammo
		"""
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"infinite_craft:laser_blaster","minecraft:item_name":{text:"Laser Blaster",color:"red"},"minecraft:max_stack_size":1,"infinite_craft:shoot":{kind:"beam",damage:6.0f,color:"red",interval:10}}}""",
		// rapid-fire bullet gun
		"""
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"infinite_craft:minigun","minecraft:item_name":{text:"Minigun",color:"gray"},"minecraft:max_stack_size":1,"infinite_craft:shoot":{kind:"bullet",damage:3.0f,color:"gold",interval:4,spread:3.0f}}}""",
		// explosive launcher: rockets fly visibly and blow up mobs where they land
		"""
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"infinite_craft:rocket_launcher","minecraft:item_name":{text:"Rocket Launcher",color:"dark_green"},"minecraft:max_stack_size":1,"infinite_craft:shoot":{kind:"rocket",damage:8.0f,explode:3.0f,interval:30}}}""",
		// melee weapon that also calls lightning on right click
		"""
		{id:"minecraft:mace",count:1i,components:{"minecraft:item_name":{text:"Thor's Hammer",color:"aqua"},"minecraft:enchantments":{"minecraft:wind_burst":1},"infinite_craft:shoot":{kind:"beam",damage:6.0f,color:"aqua",interval:30,lightning:true}}}""",
		// flying wearable
		"""
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"infinite_craft:jetpack","minecraft:item_name":{text:"Jetpack",color:"yellow"},"minecraft:max_stack_size":1,"minecraft:equippable":{slot:"chest",asset_id:"infinite_craft:jetpack"},"infinite_craft:thrust":{power:1.0f,speed:1.5f},"minecraft:attribute_modifiers":[{type:"minecraft:safe_fall_distance",id:"infinite_craft:jetpack_landing",amount:20.0d,operation:"add_value",slot:"chest"}]}}""",
		// boots that dash in mid-air
		"""
		{id:"minecraft:leather_boots",count:1i,components:{"minecraft:item_name":{text:"Dash Boots",color:"aqua"},"minecraft:dyed_color":3394815,"infinite_craft:dash":{power:1.5f,charges:2},"minecraft:attribute_modifiers":[{type:"minecraft:armor",id:"infinite_craft:dash_armor",amount:1.0d,operation:"add_value",slot:"feet"},{type:"minecraft:movement_speed",id:"infinite_craft:dash_speed",amount:0.2d,operation:"add_multiplied_base",slot:"feet"},{type:"minecraft:safe_fall_distance",id:"infinite_craft:dash_landing",amount:6.0d,operation:"add_value",slot:"feet"}]}}""",
		// armor with bonuses (modifiers replace the base item's own armor, so include armor)
		"""
		{id:"minecraft:golden_chestplate",count:1i,components:{"minecraft:item_name":{text:"Holy Chestplate",color:"gold"},"minecraft:unbreakable":{},"minecraft:enchantments":{"minecraft:protection":4},"minecraft:attribute_modifiers":[{type:"minecraft:armor",id:"infinite_craft:holy_armor",amount:10.0d,operation:"add_value",slot:"chest"},{type:"minecraft:max_health",id:"infinite_craft:holy_health",amount:10.0d,operation:"add_value",slot:"chest"}]}}""",
		// tool
		"""
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"minecraft:stonecutter","minecraft:item_name":{text:"Disc Saw",color:"gray"},"minecraft:max_stack_size":1,"minecraft:tool":{rules:[{blocks:"#minecraft:mineable/axe",speed:20.0f,correct_for_drops:true}],default_mining_speed:1.0f,damage_per_block:0}}}""",
		// gadget used several times: consumables are used up, so give a few with a cooldown
		"""
		{id:"infinite_craft:custom_crafted_item",count:4i,components:{"minecraft:item_model":"minecraft:ender_eye","minecraft:item_name":{text:"Blink Orb",color:"dark_purple"},"minecraft:max_stack_size":16,"minecraft:use_cooldown":{seconds:3.0f},"minecraft:consumable":{consume_seconds:0.1f,animation:"none",sound:"minecraft:entity.enderman.teleport",has_consume_particles:false,on_consume_effects:[{type:"minecraft:teleport_randomly",diameter:16.0f}]}}}"""
	);

	public record CraftedModel(String model, String description) {}

	private static List<CraftedModel> craftedModels;

	/** The mod's own item models, written by tools/textures/make_textures.py along with their art */
	public static List<CraftedModel> craftedModels() {
		if (craftedModels == null) {
			try (InputStreamReader reader = new InputStreamReader(
					AiPrompt.class.getResourceAsStream("/assets/infinite_craft/crafted_models.json"), StandardCharsets.UTF_8)) {
				craftedModels = JsonParser.parseReader(reader).getAsJsonArray().asList().stream()
					.map(e -> new CraftedModel(e.getAsJsonObject().get("model").getAsString(), e.getAsJsonObject().get("description").getAsString()))
					.toList();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return craftedModels;
	}

	private static String getPowerToolkitTip(){
		return """
			MAKE IT DO SOMETHING:
			\tThe player WILL try to use what you craft. Every item result must have at least one real gameplay effect that matches its name.
			\tNEVER return an item that is only a name, a model and lore.
			\tThis is about new or modified items: breaking one item down into its materials, or a VANILLAIFY result, should stay plain vanilla items.
			\tIf an input item has a custom name, the player renamed it to wish for that thing: make that thing, as powerful and fun as its name suggests.
			\tPick the base item or components that make the idea work:
			\t\tFood, drinks, potions: "minecraft:consumable" with on_consume_effects (apply_effects, teleport_randomly, clear_all_effects), optionally "minecraft:food"
			\t\tMelee weapons: "minecraft:weapon" plus attack_damage / attack_speed / attack_knockback attribute modifiers on mainhand
			\t\tAnything that shoots (guns, blasters, ray guns, lasers, wands, launchers, magic bows): "infinite_craft:shoot", this mod's component. It fires with NO ammo while right click is held.
			\t\t\tFields: kind ("beam": instant colored ray, for lasers, ray guns, wands; "bullet": fast projectile, for nail guns, machine guns, shotguns, bows;
			\t\t\t"rocket": a slower rocket you can watch fly, which always explodes, for rocket launchers, bazookas, grenade and firework launchers),
			\t\t\tdamage (per hit; a zombie has 20 health, a diamond sword does 7), interval (ticks between shots: 4 is the fastest, 20 is once a second),
			\t\t\tcount (shots per pull, e.g. 5 for a shotgun), spread (aim error in degrees), color (a color name or "#RRGGBB"), fire (true sets targets on fire), optional sound.
			\t\t\tBlasts where a shot lands: explode (explosion power 1 to 4: 2 a grenade, 3 a creeper, 4 TNT; for rocket launchers, TNT guns, grenade launchers, explosive arrows)
			\t\t\tand lightning (true: lightning strikes there; for Thor's hammer, smite wands, storm staffs). They hurt mobs but never break blocks, start fires or hurt the shooter.
			\t\t\tKeep blasting guns slow (interval 20 or more): a blast every shot is chaos.
			\t\t\tsound plays on EVERY shot, so leave it out (beams zap, bullets click) or pick a short one like "minecraft:entity.blaze.shoot"; never explosions or long sounds like beacon or music.
			\t\t\tMake different guns feel different: a chain gun fires every 4 ticks with some spread, a ray gun is a colored beam, a shotgun fires several bullets with wide spread, a sniper is slow and hits hard.
			\t\t\tGive it a fitting "minecraft:item_model" on infinite_craft:custom_crafted_item. A vanilla bow or crossbow alone needs arrows the player may not have, so add "infinite_craft:shoot" to those too.
			\t\tPunchers, hammers, anything that should send mobs flying: "infinite_craft:launch", this mod's component. A melee hit flings the mob away in a big arc with a trail.
			\t\t\tFields: power (how far: 1 is about 10 blocks, 2 about 20, 3 about 30), up (how high it arcs: 0 skims the ground, 1 is a high arc, 2 is nearly straight up).
			\t\t\tIt works on any base item and even on mobs that shrug off knockback; add attack_damage for the hit itself. Prefer it to attack_knockback, which only shoves mobs along the ground.
			\t\t\tminecraft:mace (wind_burst, density, breach) suits a smashing hammer. A magic hammer can also shoot, like Thor's hammer calling lightning.
			\t\tArmor and wearables: a vanilla armor base or "minecraft:equippable", with armor, max_health, movement_speed, jump_strength, scale, safe_fall_distance modifiers on that slot.
			\t\t\tAttribute modifiers REPLACE the base armor's own stats, so give at least the base item's armor and toughness (e.g. iron chestplate 6 armor, diamond chestplate 8 armor + 2 toughness) plus the extras.
			\t\tDashing (dash boots, speed boots, ninja gear, double-jump shoes): "infinite_craft:dash", this mod's component, on a worn item. Pressing jump again in mid-air dashes the way the player faces.
			\t\t\tFields: power (how far: 1 is about 8 blocks, 3 about 20), charges (dashes before landing again: 2 is a double dash). Pair it with movement_speed or a safe_fall_distance modifier.
			\t\tFlying under power (jetpacks, rocket boots, flying armor): "infinite_craft:thrust", this mod's component, on an item with "minecraft:equippable".
			\t\t\tWhile it is worn, holding jump flies up and holding forward zooms ahead. Fields: power (lift, 1 climbs about 7 blocks a second), speed (forward boost, 0 to 3).
			\t\t\tAdd a safe_fall_distance modifier so landing doesn't hurt. For a jetpack worn on the back, give equippable asset_id:"infinite_craft:jetpack" so players see it.
			\t\tGliding (wings, hang gliders): "minecraft:glider" with an equippable chest slot, or a minecraft:elytra base. Don't put glider and thrust on the same item.
			\t\tTools: "minecraft:tool" with mining rules
			\t\tMagic gadgets: a "minecraft:consumable" effect plus "minecraft:use_cooldown"; consumables are used up, so give several (count 2-8)
			\t\tSaving the player from death: "minecraft:death_protection" (works like a totem, but ONLY while held in a hand, never on armor)
			\tLooks: "minecraft:item_model" sets how an item looks. This mod has its own models; use one whenever it fits, it looks far better than a borrowed vanilla item:
			""" + String.join("\n", craftedModels().stream().map(m -> "\t\t\"%s\": %s".formatted(m.model(), m.description())).toList()) + """

			\t\tTheir glowing parts take the color the item shoots, or the first color in "minecraft:custom_model_data":{colors:[RGB int]}; set that for items that don't shoot, like a red lightsaber.
			\t\tOtherwise use a vanilla item id as the model. Never make up a model id: it shows as a broken purple and black texture.
			\tWorking examples for this version:
			""" + String.join("\n", POWER_EXAMPLES.stream().map(e -> "\t\t" + e.strip()).toList()) + "\n";
	}

	private static String getCraftElementTip(){
		return """
			If you think the result should be an ELEMENT, like the original Infinite Craft game, then describe the element in {"name":$name,"emoji":$emoji,"color":$color,"model":$model} format in `element`, and put nothing into `itemNbt`.
			\tFor example, the user input element `Wind` and `Water`, you should return {"success": true, "element": {"name":"Wave","emoji":"🌊","color":"aqua","model":"minecraft:snow_block"}, "itemNbt": ""}
			\tIf the element name have multiple words, simply separate them with spaces.
			""";
	}

    public static JsonObject getPromptJsonSchema() {
        JsonObject result = promptJsonSchema.deepCopy();
		result.addProperty("description", "The crafting result, YOU MUST USE LANGUAGE %s".formatted(InfiniteCraft.config.Language));
		return result;
    }

	private enum CraftingType {
		CRAFT_ITEM,
		VANILLAIFY,
		BREAK_DOWN_ITEM,
		CRAFT_ELEMENT,
		CRAFT_EITHER
	}
}
