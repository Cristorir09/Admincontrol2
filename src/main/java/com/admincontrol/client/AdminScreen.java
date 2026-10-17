package com.admincontrol.client;

import com.admincontrol.Config;
import com.admincontrol.Matchers;
import com.admincontrol.Net;
import com.admincontrol.RuleDefs;
import com.admincontrol.gui.Categories;
import com.admincontrol.gui.Icons;
import com.admincontrol.gui.Kind;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Panel de administración dibujado a mano (modo inmediato). Edita una copia de la config y la envía al servidor. */
public class AdminScreen extends Screen {
    private static final Gson GSON = new Gson();

    // ---------------- paleta ----------------
    private static final int C_PANEL = 0xFF171923, C_PANEL2 = 0xFF212434, C_SIDE = 0xFF11131A, C_LINE = 0xFF30344A,
            C_ACC = 0xFF7C5CFF, C_ACC2 = 0xFF2BB6D6, C_OK = 0xFF2EB872, C_BAD = 0xFFE0494F, C_WARN = 0xFFE59A2E,
            C_TEXT = 0xFFEDEEF5, C_MUTED = 0xFF8D91A6, C_BTN = 0xFF2B3047;

    // ---------------- datos del servidor ----------------
    private Config cfg = new Config();
    private String selfUuid = "";
    private record PInfo(String uuid, String name, boolean admin, boolean vanished) {}
    private record Group(String host, List<String> members) {}
    private record GRule(String name, boolean value) {}
    private final List<PInfo> players = new ArrayList<>();
    private final List<Group> plan = new ArrayList<>();
    private final List<GRule> gamerules = new ArrayList<>();
    private boolean running;
    private int returnable;

    // ---------------- estado de la interfaz ----------------
    private record Sec(String id, String title, Item icon) {}
    private static final List<Sec> SECTIONS = List.of(
            new Sec("items", "Items", Items.BARRIER),
            new Sec("spawn", "Spawn", Items.ZOMBIE_HEAD),
            new Sec("damage", "Daño", Items.IRON_SWORD),
            new Sec("rules", "Reglas", Items.TNT),
            new Sec("players", "Jugadores", Items.PLAYER_HEAD),
            new Sec("event", "Evento", Items.ENDER_PEARL),
            new Sec("admin", "Vanish / Admin", Items.ENDER_EYE));

    private record Cat(String id, String title, Item icon) {}
    private static final List<Cat> CATS = List.of(
            new Cat("building", "Construcción", Items.BRICKS), new Cat("decor", "Decoración", Items.POPPY),
            new Cat("redstone", "Redstone", Items.REDSTONE), new Cat("tools", "Herramientas", Items.IRON_PICKAXE),
            new Cat("combat", "Combate", Items.IRON_SWORD), new Cat("armor", "Armadura", Items.IRON_CHESTPLATE),
            new Cat("food", "Comida", Items.APPLE), new Cat("materials", "Materiales", Items.IRON_INGOT),
            new Cat("eggs", "Huevos de mob", Items.ZOMBIE_SPAWN_EGG), new Cat("blocked", "Bloqueados", Items.BARRIER),
            new Cat("all", "Todos", Items.CHEST));

    private String section = "items";
    private String itemView = "grid", itemCat = "building";
    private int itemPage;
    private Identifier selItem;
    private String spawnTab = "blocked", editRule = null, editBan = null;
    private String ruleTab = "switch", ruleCat = "use";
    private String eventTab = "prep";
    private boolean confirmRun;
    private String excPlayer = null;

    private final Map<String, StringBuilder> fields = new HashMap<>();
    private final Map<String, Runnable> submits = new HashMap<>();
    private String active = null;
    private final Map<String, Integer> scroll = new HashMap<>();
    private boolean dirty;
    private int ver;

    // picker (ventana modal para elegir items, mobs, biomas, jugadores)
    private Consumer<String> pickAction;
    private Kind pickKind;
    private boolean pickPlayers, pickClose;
    private String pickTitle = "";
    private Predicate<String> pickMarked;
    private List<String> pickAll = List.of(), pickShown = List.of();
    private String pickQuery = "\u0000";

    // item grid cache
    private List<Identifier> itemCache = List.of();
    private String itemCacheKey = "";
    private final Map<Identifier, ItemStack> stackCache = new HashMap<>();
    private final Map<String, ItemStack> iconCache = new HashMap<>();

    // ---------------- inmediato ----------------
    private DrawContext g;
    private int mx, my, layer, pw, ph, px, py;
    private record Hit(int x, int y, int w, int h, int layer, Runnable run) {}
    private record Area(String key, int x, int y, int w, int h, int layer) {}
    private final List<Hit> hits = new ArrayList<>();
    private final List<Area> areas = new ArrayList<>();
    private List<Text> tip;

    private record RB(String text, int color, Runnable act) {}
    private record Row(ItemStack icon, String label, String sub, List<RB> btns, Runnable click) {}
    private record Tog(String label, String desc, BooleanSupplier get, Consumer<Boolean> set) {}

    public AdminScreen(String json) {
        super(Text.literal("Admin Control"));
        applyState(json);
    }

    // =====================================================================
    // Estado
    // =====================================================================
    public void applyState(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        cfg = GSON.fromJson(o.get("config"), Config.class);
        cfg.normalize();
        selfUuid = o.get("self").getAsString();
        players.clear();
        for (JsonElement e : o.getAsJsonArray("players")) {
            JsonObject j = e.getAsJsonObject();
            players.add(new PInfo(j.get("uuid").getAsString(), j.get("name").getAsString(),
                    j.get("admin").getAsBoolean(), j.get("vanished").getAsBoolean()));
        }
        plan.clear();
        for (JsonElement e : o.getAsJsonArray("plan")) {
            JsonObject j = e.getAsJsonObject();
            List<String>
