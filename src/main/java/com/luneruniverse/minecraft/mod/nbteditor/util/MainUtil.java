package com.luneruniverse.minecraft.mod.nbteditor.util;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.zip.ZipException;

import com.google.gson.JsonParseException;
import com.luneruniverse.minecraft.mod.nbteditor.NBTEditorClient;
import com.luneruniverse.minecraft.mod.nbteditor.async.UpdateCheckerThread;
import com.luneruniverse.minecraft.mod.nbteditor.misc.Shaders.MVShader;
import com.luneruniverse.minecraft.mod.nbteditor.multiversion.MVDrawableHelper;
import com.luneruniverse.minecraft.mod.nbteditor.multiversion.MVMatrix4f;
import com.luneruniverse.minecraft.mod.nbteditor.multiversion.MVMisc;
import com.luneruniverse.minecraft.mod.nbteditor.multiversion.MVRegistry;
import com.luneruniverse.minecraft.mod.nbteditor.multiversion.TextInst;
import com.luneruniverse.minecraft.mod.nbteditor.multiversion.nbt.NBTManagers;
import com.mojang.blaze3d.systems.RenderSystem;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public class MainUtil {
	
	public static final MinecraftClient client = MinecraftClient.getInstance();
	
	// Same as ClientPlayerInteractionManager#clickCreativeSlot, but without a feature flag check
	// Also includes survival bypass
	public static void clickCreativeStack(ItemStack item, int slot) {
		if (NBTEditorClient.SERVER_CONN.isEditingAllowed())
			MVMisc.sendC2SPacket(new CreativeInventoryActionC2SPacket(slot, item));
	}
	public static void dropCreativeStack(ItemStack item) {
		if (NBTEditorClient.SERVER_CONN.isEditingAllowed() && !item.isEmpty())
			MVMisc.sendC2SPacket(new CreativeInventoryActionC2SPacket(-1, item));
	}
	
	public static void saveItem(Hand hand, ItemStack item) {
		client.player.setStackInHand(hand, item.copy());
		clickCreativeStack(item, hand == Hand.OFF_HAND ? 45 : client.player.getInventory().selectedSlot + 36);
	}
	public static void saveItem(EquipmentSlot equipment, ItemStack item) {
		if (equipment == EquipmentSlot.MAINHAND)
			saveItem(Hand.MAIN_HAND, item);
		else if (equipment == EquipmentSlot.OFFHAND)
			saveItem(Hand.OFF_HAND, item);
		else {
			client.player.getInventory().armor.set(equipment.getEntitySlotId(), item.copy());
			clickCreativeStack(item, 8 - equipment.getEntitySlotId());
		}
	}
	
	public static void saveItem(int slot, ItemStack item) {
		client.player.getInventory().setStack(slot == 45 ? 40 : slot, item.copy());
		clickCreativeStack(item, slot < 9 ? slot + 36 : slot);
	}
	public static void saveItemInvSlot(int slot, ItemStack item) {
		saveItem(slot == 45 ? 45 : (slot >= 36 ? slot - 36 : slot), item);
	}
	
	public static void get(ItemStack item, boolean dropIfNoSpace) {
		PlayerInventory inv = client.player.getInventory();
		item = item.copy();
		
		int slot = inv.getOccupiedSlotWithRoomForStack(item);
		if (slot == -1)
			slot = inv.getEmptySlot();
		if (slot == -1) {
			if (dropIfNoSpace) {
				if (item.getCount() > item.getMaxCount())
					item.setCount(item.getMaxCount());
				dropCreativeStack(item);
			}
		} else {
			item.setCount(item.getCount() + inv.getStack(slot).getCount());
			int overflow = 0;
			if (item.getCount() > item.getMaxCount()) {
				overflow = item.getCount() - item.getMaxCount();
				item.setCount(item.getMaxCount());
			}
			saveItem(slot == 40 ? 45 : slot, item);
			if (overflow != 0) {
				item.setCount(overflow);
				get(item, false);
			}
		}
	}
	public static void getWithMessage(ItemStack item) {
		get(item, true);
		client.player.sendMessage(TextInst.translatable("nbteditor.get.item").append(item.toHoverableText()), false);
	}
	
	
	
	private static final Identifier LOGO = new Identifier("nbteditor", "textures/logo.png");
	private static final Identifier LOGO_UPDATE_AVAILABLE = new Identifier("nbteditor", "textures/logo_update_available.png");
	public static void renderLogo(MatrixStack matrices) {
		MVDrawableHelper.drawTexture(matrices,
				UpdateCheckerThread.UPDATE_AVAILABLE ? LOGO_UPDATE_AVAILABLE : LOGO, 16, 16, 0, 0, 32, 32, 32, 32);
	}
	
	
	
	public static void drawWrappingString(MatrixStack matrices, TextRenderer renderer, String text, int x, int y, int maxWidth, int color, boolean centerHorizontal, boolean centerVertical) {
		maxWidth = Math.max(maxWidth, renderer.getWidth("ww"));
		
		// Split into breaking spots
		List<String> parts = new ArrayList<>();
		List<Integer> spaces = new ArrayList<>();
		StringBuilder currentPart = new StringBuilder();
		boolean wasUpperCase = false;
		for (char c : text.toCharArray()) {
			if (c == ' ') {
				wasUpperCase = false;
				parts.add(currentPart.toString());
				currentPart.setLength(0);
				spaces.add(parts.size());
				continue;
			}
			
			boolean upperCase = Character.isUpperCase(c);
			if (upperCase != wasUpperCase && !currentPart.isEmpty()) { // Handle NBTEditor; output NBT, Editor; not N, B, T, Editor AND Handle MinionYT; output Minion YT
				if (wasUpperCase) {
					parts.add(currentPart.substring(0, currentPart.length() - 1));
					currentPart.delete(0, currentPart.length() - 1);
				} else {
					parts.add(currentPart.toString());
					currentPart.setLength(0);
				}
			}
			wasUpperCase = upperCase;
			currentPart.append(c);
		}
		if (!currentPart.isEmpty())
			parts.add(currentPart.toString());
		
		// Generate lines, maximizing the number of parts per line
		List<String> lines = new ArrayList<>();
		String line = "";
		int i = 0;
		for (String part : parts) {
			String partAddition = (!line.isEmpty() && spaces.contains(i) ? " " : "") + part;
			if (renderer.getWidth(line + partAddition) > maxWidth) {
				if (!line.isEmpty()) {
					lines.add(line);
					line = "";
				}
				
				if (renderer.getWidth(part) > maxWidth) {
					while (true) {
						int numChars = 1;
						while (renderer.getWidth(part.substring(0, numChars)) < maxWidth)
							numChars++;
						numChars--;
						lines.add(part.substring(0, numChars));
						part = part.substring(numChars);
						if (renderer.getWidth(part) < maxWidth) {
							line = part;
							break;
						}
					}
				} else
					line = part;
			} else
				line += partAddition;
			i++;
		}
		if (!line.isEmpty())
			lines.add(line);
		
		
		// Draw the lines
		for (i = 0; i < lines.size(); i++) {
			line = lines.get(i);
			int offsetY = i * renderer.fontHeight + (centerVertical ? -renderer.fontHeight * lines.size() / 2 : 0);
			if (centerHorizontal)
				MVDrawableHelper.drawCenteredTextWithShadow(matrices, renderer, TextInst.of(line), x, y + offsetY, color);
			else
				MVDrawableHelper.drawTextWithShadow(matrices, renderer, TextInst.of(line), x, y + offsetY, color);
		}
	}
	
	
	public static String colorize(String text) {
		StringBuilder output = new StringBuilder();
		boolean colorCode = false;
		for (char c : text.toCharArray()) {
			if (c == '&')
				colorCode = true;
			else {
				if (colorCode) {
					colorCode = false;
					if ((c + "").replaceAll("[0-9a-fA-Fk-oK-OrR]", "").isEmpty())
						output.append('§');
					else
						output.append('&');
				}
				
				output.append(c);
			}
		}
		if (colorCode)
			output.append('&');
		return output.toString();
	}
	public static String stripColor(String text) {
		return text.replaceAll("\\xA7[0-9a-fA-Fk-oK-OrR]", "");
	}
	
	
	public static Text getItemNameSafely(ItemStack item) {
		NbtCompound nbt = item.getSubNbt(ItemStack.DISPLAY_KEY);
		return getNbtNameSafely(nbt, ItemStack.NAME_KEY, () -> item.getItem().getName(item));
	}
	public static Text getNbtNameSafely(NbtCompound nbt, String key, Supplier<Text> defaultName) {
		if (nbt != null && nbt.contains(key, NbtElement.STRING_TYPE)) {
            try {
                Text text = TextInst.fromJson(nbt.getString(key));
                if (text != null)
                    return text;
            } catch (JsonParseException e) {}
        }
        return defaultName.get();
	}
	
	
	public static DyeColor getDyeColor(Formatting color) {
		switch (color) {
			case AQUA:
				return DyeColor.LIGHT_BLUE;
			case BLACK:
				return DyeColor.BLACK;
			case BLUE:
				return DyeColor.BLUE;
			case DARK_AQUA:
				return DyeColor.CYAN;
			case DARK_BLUE:
				return DyeColor.BLUE;
			case DARK_GRAY:
				return DyeColor.GRAY;
			case DARK_GREEN:
				return DyeColor.GREEN;
			case DARK_PURPLE:
				return DyeColor.PURPLE;
			case DARK_RED:
				return DyeColor.RED;
			case GOLD:
				return DyeColor.ORANGE;
			case GRAY:
				return DyeColor.LIGHT_GRAY;
			case GREEN:
				return DyeColor.LIME;
			case LIGHT_PURPLE:
				return DyeColor.PINK;
			case RED:
				return DyeColor.RED;
			case WHITE:
				return DyeColor.WHITE;
			case YELLOW:
				return DyeColor.YELLOW;
			default:
				return DyeColor.BROWN;
		}
	}
	
	
	public static ItemStack copyAirable(ItemStack item) {
		ItemStack output = new ItemStack(item.getItem(), item.getCount());
		output.setBobbingAnimationTime(item.getBobbingAnimationTime());
		if (item.manager$hasNbt())
			output.manager$setNbt(item.manager$getNbt());
		return output;
	}
	
	
	public static ItemStack setType(Item type, ItemStack item, int count) {
		NbtCompound fullData = item.manager$serialize();
		fullData.putString("id", MVRegistry.ITEM.getId(type).toString());
		fullData.putInt(NBTManagers.COMPONENTS_EXIST ? "count" : "Count", count);
		return NBTManagers.ITEM.deserialize(fullData);
	}
	public static ItemStack setType(Item type, ItemStack item) {
		return setType(type, item, item.getCount());
	}
	
	
	@SuppressWarnings("unchecked")
	public static <T> Event<T> newEvent(Class<T> clazz) {
		return EventFactory.createArrayBacked(clazz, listeners -> {
			return (T) Proxy.newProxyInstance(MainUtil.class.getClassLoader(), new Class<?>[] {clazz}, (obj, method, args) -> {
				for (T listener : listeners) {
					ActionResult result = (ActionResult) method.invoke(listener, args);
					if (result != ActionResult.PASS)
						return result;
				}
				return ActionResult.PASS;
			});
		});
	}
	
	
	public static NbtCompound readNBT(InputStream in) throws IOException {
		byte[] data = in.readAllBytes();
		DataInputStream resetableIn = new DataInputStream(new ByteArrayInputStream(data));
		NbtCompound nbt;
		try {
			nbt = MVMisc.readCompressedNbt(resetableIn);
		} catch (ZipException e) {
			resetableIn.reset();
			nbt = NbtIo.readCompound(resetableIn);
		}
		return nbt;
	}
	
	
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);
	public static String getFormattedCurrentTime() {
		return DATE_TIME_FORMATTER.format(ZonedDateTime.now());
	}
	
	
	public static boolean equals(double a, double b, double epsilon) {
		return Math.abs(a - b) <= epsilon;
	}
	public static boolean equals(double a, double b) {
		return equals(a, b, 1E-5);
	}
	
	
	public static BufferedImage scaleImage(BufferedImage img, int width, int height) {
		Image temp = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
		BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = output.createGraphics();
		g.drawImage(temp, 0, 0, null);
		g.dispose();
		return output;
	}
	
	
	public static int[] getMousePos() {
		double scale = (double) client.getWindow().getScaledWidth() / client.getWindow().getWidth();
		int x = (int) (client.mouse.getX() * scale);
		int y = (int) (client.mouse.getY() * scale);
		return new int[] {x, y};
	}
	
	
	public static void mapMatrices(MatrixStack matrices,
			int fromX, int fromY, int fromWidth, int fromHeight,
			int toX, int toY, int toWidth, int toHeight) {
		matrices.translate(toX, toY, 0.0);
		matrices.scale((float) toWidth / fromWidth, (float) toHeight / fromHeight, 1);
		matrices.translate(-fromX, -fromY, 0.0);
	}
	
	
	public static Predicate<String> intPredicate(Supplier<Integer> min, Supplier<Integer> max, boolean allowEmpty) {
		return str -> {
			if (str.isEmpty() || str.equals("+") || str.equals("-"))
				return allowEmpty;
			try {
				int value = Integer.parseInt(str);
				return (min == null || min.get() <= value) && (max == null || value <= max.get());
			} catch (NumberFormatException e) {
				return false;
			}
		};
	}
	public static Predicate<String> intPredicate(Integer min, Integer max, boolean allowEmpty) {
		return intPredicate(() -> min, () -> max, allowEmpty);
	}
	public static Predicate<String> intPredicate() {
		return intPredicate((Supplier<Integer>) null, null, true);
	}
	
	public static Integer parseOptionalInt(String str) {
		try {
			return Integer.parseInt(str);
		} catch (NumberFormatException e) {
			return null;
		}
	}
	public static int parseDefaultInt(String str, int defaultValue) {
		Integer output = parseOptionalInt(str);
		if (output == null)
			return defaultValue;
		return output;
	}
	
	
	public static void fillShader(MatrixStack matrices, MVShader shader, Consumer<VertexConsumer> data, int x, int y, int width, int height) {
		int x1 = x;
		int y1 = y;
		int x2 = x + width;
		int y2 = y + height;
		
		MVMatrix4f matrix = MVMatrix4f.getPositionMatrix(matrices.peek());
		VertexConsumer vertex = MVMisc.beginDrawingShader(matrices, shader);
		
		matrix.applyToVertex(vertex, x1, y1, 0).texture(0, 0);
		data.accept(vertex);
		vertex.next();
		
		matrix.applyToVertex(vertex, x1, y2, 0).texture(0, 1);
		data.accept(vertex);
		vertex.next();
		
		matrix.applyToVertex(vertex, x2, y2, 0).texture(1, 1);
		data.accept(vertex);
		vertex.next();
		
		matrix.applyToVertex(vertex, x2, y1, 0).texture(1, 0);
		data.accept(vertex);
		vertex.next();
		
		RenderSystem.disableDepthTest();
		MVMisc.endDrawingShader(matrices, vertex);
		RenderSystem.enableDepthTest();
	}
	
}
