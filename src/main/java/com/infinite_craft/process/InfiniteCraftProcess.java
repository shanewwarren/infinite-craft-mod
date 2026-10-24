package com.infinite_craft.process;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.infinite_craft.InfiniteCraft;
import com.infinite_craft.ai.AiPrompt;
import com.infinite_craft.element.ElementComponentType;
import com.infinite_craft.element.ElementData;
import com.infinite_craft.element.ElementItems;
import com.infinite_craft.element.PlayerEntityExt;
import com.infinite_craft.element.globaldata.GlobalDiscoveringDataManager;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class InfiniteCraftProcess {

    /**
     * 异步执行请求并给予结果
     */
    public static void requestCraftResult(MinecraftServer server, ServerPlayerEntity player, BlockPos pos, ServerWorld dimension, CraftingScreenHandler handler) {

        // 🧱 1. Get Input Items
        int gridSize = 3;
        int slotCount = gridSize * gridSize;
        int minInputItemStack=0xff;
        final int exceptedTryCraftTicksF = 20 * 120;

        ArrayList<Slot> inputs = new ArrayList<>();
        ArrayList<ItemStack> usedItem = new ArrayList<>();
        for (int i = 1; i <= slotCount; i++) {
            inputs.add(handler.getSlot(i));
        }
        for (int i = 0; i < slotCount; i++) {
            Slot slot=inputs.get(i);
            if (slot.getStack().isEmpty()) continue;
            minInputItemStack=Math.min(slot.getStack().getCount(), minInputItemStack);
        }
        InfiniteCraft.LOGGER.info("Min Input Item Stack: {}", minInputItemStack);
        if(minInputItemStack==0xff) return;
        for (int i = 0; i < slotCount; i++) {
            Slot slot=inputs.get(i);
            if (slot.getStack().isEmpty()) continue;
            usedItem.add(slot.getStack().copyWithCount(minInputItemStack));
            slot.takeStack(minInputItemStack);
        }
        final int finalMinInputItemStack=minInputItemStack;
        String playerName = player.getGameProfile().name();
        new Thread(() -> {
            try {
                int exceptedTryCraftTicks = exceptedTryCraftTicksF;
                double progressStart=0;
                double progressCompleteRate=0.6;
                final double progressTarget=100;
                LoadingState loadingState = new LoadingState(player, progressStart, progressCompleteRate, progressTarget);
                loadingState.newLoadingProcessCustomEnd(1, 10);

                // 🧠 2. Gemerate prompt
                String prompt = AiPrompt.GeneratePrompt(usedItem, gridSize);

                // 🌐 3. Ask
                ItemStack response = postWithRetry(prompt, player, 3, loadingState, exceptedTryCraftTicks);

                // 🎁 4. Send Result
                loadingState.complete(5);
                if (response != null) {
                    InfiniteCraft.LOGGER.info("Request Result:\n{}", response.toString());
                    if (!response.isEmpty()) {
                        server.execute(() -> { // 回到主线程
                            for (int i = 0; i < finalMinInputItemStack; i++) {
                                if (player != null && player.isAlive()) {
                                    player.sendMessage(Text.translatableWithFallback("chat.message.infinite_craft.craft.success","Crafted: ")
                                        .setStyle(
                                            Style.EMPTY.withColor(TextColor.parse("lime").result().orElse(TextColor.fromRgb(0xbfff00)))
                                        )
                                        .append(
                                            DescribeItemStack(response)
                                            .setStyle(
                                                Style.EMPTY.withColor(TextColor.parse("white").result().orElse(TextColor.fromRgb(0xffffff)))
                                            )
                                        ),
                                    false);
                                    ItemStack copiedItemStack = response.copy();
                                    if(ElementData.isElement(copiedItemStack)){
                                        ElementComponentType elementComponentType = ElementData.fromItem(copiedItemStack).get().generateElementComponent();
                                        ((PlayerEntityExt)player).getDiscoveringData().add(elementComponentType);
                                        GlobalDiscoveringDataManager.get(server).getDiscovered().add(elementComponentType);
                                    }
                                    if (!player.getInventory().insertStack(copiedItemStack)) {
                                        player.dropItem(copiedItemStack, false);
                                    }
                                } else {
                                    ServerWorld world = dimension;
                                    if (world != null) {
                                        ItemStack copiedItemStack = response.copy();
                                        ItemEntity entity = new ItemEntity(world,
                                                pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                                                copiedItemStack);
                                        world.spawnEntity(entity);
                                    }
                                }
                            }
                        });
                    }
                } else {
                    InfiniteCraft.LOGGER.info("Request Failed\n");
                    server.execute(() -> {
                        for(ItemStack result : usedItem){
                            if(result!=null && !result.isEmpty()){
                                if (player != null && player.isAlive()) {
                                    player.sendMessage(Text.translatableWithFallback("chat.message.infinite_craft.craft.return","Returned: ")
                                        .setStyle(
                                            Style.EMPTY.withColor(TextColor.parse("lime").result().orElse(TextColor.fromRgb(0xbfff00)))
                                        )
                                        .append(
                                            DescribeItemStack(result)
                                            .setStyle(
                                                Style.EMPTY.withColor(TextColor.parse("white").result().orElse(TextColor.fromRgb(0xffffff)))
                                            )
                                        ),
                                    false);
                                    if (!player.getInventory().insertStack(result)) {
                                        player.dropItem(result, false);
                                    }
                                } else {
                                    ServerWorld world = dimension;
                                    if (world != null) {
                                        ItemEntity entity = new ItemEntity(world,
                                                pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                                                result);
                                        world.spawnEntity(entity);
                                    }
                                }
                            }
                        }
                    });
                }
            } catch (Exception e) {
                InfiniteCraft.LOGGER.error(e.getMessage());
            }
        }, "InfiniteCraft-%s-%s".formatted(playerName, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")))).start();
    }

    /**
     * Ask Ai and return a item stack
     */
    private static ItemStack postWithRetry(String prompt, ServerPlayerEntity player, int maxRetries, LoadingState loadingState, int exceptedTryCraftTicks) {
        for (int i = 0; i < maxRetries; i++) {
            loadingState.newLoadingProcess(exceptedTryCraftTicks);
            try {
                JsonObject response = AiApi.doPost(prompt, player);
                InfiniteCraft.LOGGER.info("API Response:\n{}", response.toString());
                if (response.has("success") && response.has("itemNbt")) {
                    if(response.get("success").getAsBoolean()==false) return null;
                    try {
                        String nbtString = response.get("itemNbt").getAsString();
                        InfiniteCraft.LOGGER.info("Item NBT:\n"+nbtString);
                        if(!response.has("element")){
                            return parseItemStackFromNbt(nbtString, player.getRegistryManager());
                        } else {
                            JsonObject elementData = response.get("element").getAsJsonObject();
                            return ElementItems.generateElement(new ElementData(
                                    elementData.get("emoji").getAsString().replaceAll("\uFE0F", ""),
                                    elementData.get("name").getAsString(),
                                    elementData.get("color").getAsString(),
                                    Optional.ofNullable(Identifier.tryParse(elementData.get("model").getAsString())).orElseThrow(),
                                    elementData.has("translated") ?
                                        elementData.get("translated").getAsString() :
                                        null
                                ).checked(
                                    GlobalDiscoveringDataManager.get(player.getEntityWorld().getServer()).getDiscovered()
                                ));
                        }
                    } catch (NullPointerException e) {
                        throw new AiProblemException("The AI api is kinda broken,Illegal Element!");
                    } catch (Exception e) {
                        InfiniteCraft.LOGGER.error(e.getMessage());
                        throw new AiProblemException("The AI is too dumb!"+e.getMessage());
                    }
                }
                throw new AiProblemException("The AI api is kinda broken,Illegal Response!");
            } catch (Exception e) {
                System.err.println("[InfiniteCraft] Request failed ( Retry " + (i + 1) + " ): " + e.getMessage());
                try {
                    Thread.sleep(1000L * (i + 1));
                } catch (InterruptedException ignored) {}
            }
        }
        return null;
    }

    /**
     * Get ItemStack from an NBT String
     * Needs the world's registries: enchantments, sounds etc. are registry entries and get dropped without them
     */
    static ItemStack parseItemStackFromNbt(String nbtString, RegistryWrapper.WrapperLookup registries) throws RuntimeException, CommandSyntaxException {
        InfiniteCraft.LOGGER.info("Parsing Item Stack From:\n" + nbtString);
        NbtCompound nbt = StringNbtReader.readCompound(nbtString);
        ItemStack itemStack = ItemStack.CODEC.parse(registries.getOps(NbtOps.INSTANCE), nbt)
            .resultOrPartial(error -> {
                // Test if the id exist
                if (nbt.contains("id") && nbt.get("id").getType()==NbtElement.STRING_TYPE) {
                    Identifier id = Identifier.of(nbt.getString("id").get());
                    Item item = Registries.ITEM.get(id);
                    if (item != Items.AIR) {
                        InfiniteCraft.LOGGER.warn("Fallback to minimal ItemStack for id '{}': {}", id, error);
                        return; // ignore the component and continue
                    }
                }
                // Throw the exception if the id don't exist
                throw new RuntimeException("Failed to parse ItemStack: " + error);
            })
            .orElse(ItemStack.EMPTY);
        return itemStack;
    }

    private static MutableText DescribeItemStack(ItemStack stack){
        return Text.literal(String.valueOf(stack.getCount())+" * ")
            .setStyle(Style.EMPTY.withHoverEvent(
                new HoverEvent.ShowItem(stack)
            ))
            .append(
                stack.getName()
            );
    }
}