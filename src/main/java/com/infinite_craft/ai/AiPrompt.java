package com.infinite_craft.ai;

import java.util.ArrayList;
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
			""".formatted(gameVersion, gameVersion, gameVersion, gameVersion, Registries.ITEM.getId(InfiniteItem.CUSTOM_CRAFTED_ITEM).toString());
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
