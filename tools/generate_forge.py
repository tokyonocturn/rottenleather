#!/usr/bin/env python3
"""Generates the per-version Forge module files from the shared templates below.

Only genuinely version-specific values differ: the Forge build id, mappings, Gradle/ForgeGradle
generation, the item registry field name (1.8 vs 1.9+), the ItemStack count API (1.11+), the chat
component class (1.11+) and the ICommand interface (1.12). The behaviour lives in forge/shared and
shared/, so all eleven modules run the same code.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# mc, forge version, ForgeGradle, mappings, gradle distribution, registry field, modern ItemStack API
VERSIONS = [
    ("1.8.8", "1.8.8-11.15.0.1655", "2.1-SNAPSHOT", "stable_16", "2.14.1", "itemRegistry", False),
    ("1.8.9", "1.8.9-11.15.1.2318-1.8.9", "2.1-SNAPSHOT", "stable_20", "2.14.1", "itemRegistry", False),
    ("1.9", "1.9-12.16.1.1938-1.9.0", "2.1-SNAPSHOT", "stable_24", "2.14.1", "REGISTRY", False),
    ("1.9.4", "1.9.4-12.17.0.2317-1.9.4", "2.2-SNAPSHOT", "stable_26", "3.5", "REGISTRY", False),
    ("1.10", "1.10-12.18.0.2000-1.10.0", "2.2-SNAPSHOT", "stable_29", "3.5", "REGISTRY", False),
    ("1.10.2", "1.10.2-12.18.3.2511", "2.2-SNAPSHOT", "stable_29", "3.5", "REGISTRY", False),
    ("1.11", "1.11-13.19.1.2199", "2.2-SNAPSHOT", "stable_31", "3.5", "REGISTRY", True),
    ("1.11.2", "1.11.2-13.20.1.2588", "2.2-SNAPSHOT", "stable_32", "3.5", "REGISTRY", True),
    ("1.12", "1.12-14.21.1.2443", "2.3-SNAPSHOT", "stable_39", "4.10.3", "REGISTRY", True),
    ("1.12.1", "1.12.1-14.22.1.2485", "2.3-SNAPSHOT", "stable_39", "4.10.3", "REGISTRY", True),
    ("1.12.2", "1.12.2-14.23.5.2847", "2.3-SNAPSHOT", "stable_39", "4.10.3", "REGISTRY", True),
]

BUILD_GRADLE = """buildscript {{
    repositories {{
        maven {{ url = "https://maven.minecraftforge.net/" }}
        mavenCentral()
    }}
    dependencies {{
        classpath 'net.minecraftforge.gradle:ForgeGradle:{fg}'
    }}
}}
apply plugin: 'net.minecraftforge.gradle.forge'

version = "1.0.2"
group = "com.rottenleather"
archivesBaseName = "rottenleather-{mc}"

sourceCompatibility = targetCompatibility = '1.8'
compileJava {{
    sourceCompatibility = targetCompatibility = '1.8'
    options.encoding = 'UTF-8'
}}

minecraft {{
    version = "{forge}"
    runDir = "run"
    mappings = "{mappings}"
}}

// The adapter, @Mod entry and command adapter live here; recipe, config and cook-time behaviour are
// shared by every release and by the platform-independent core suite.
sourceSets {{
    main {{
        java.srcDirs = ['src/main/java', '../../../shared/src/main/java', '../../shared/src/main/java']
    }}
    verify {{
        java.srcDirs = ['../../../shared/src/test/java', '../../verify/java']
    }}
}}
sourceSets.verify.compileClasspath = sourceSets.main.compileClasspath + sourceSets.main.output
sourceSets.verify.runtimeClasspath = sourceSets.main.runtimeClasspath + sourceSets.main.output + sourceSets.verify.output
verifyClasses.dependsOn 'classes'

processResources {{
    inputs.property "version", project.version
    inputs.property "mcversion", project.minecraft.version
    from(sourceSets.main.resources.srcDirs) {{
        include 'mcmod.info'
        expand 'version': project.version, 'mcversion': project.minecraft.version
    }}
    from(sourceSets.main.resources.srcDirs) {{
        exclude 'mcmod.info'
    }}
}}

task verifyCore(type: JavaExec, dependsOn: 'verifyClasses') {{
    main = 'com.rottenleather.test.CoreSuite'
    classpath = sourceSets.verify.runtimeClasspath
}}

task verifyForge(type: JavaExec, dependsOn: 'verifyClasses') {{
    main = 'com.rottenleather.test.ForgeSuite'
    classpath = sourceSets.verify.runtimeClasspath
    systemProperty 'rottenleather.mc', '{mc}'
}}

jar {{
    manifest {{
        attributes(
            'Mod-Version': project.version,
            'Implementation-Title': 'Rotten Flesh to Leather',
            'Implementation-Version': project.version,
            'Implementation-Vendor': 'rottenleather'
        )
    }}
}}
"""

GRADLE_PROPERTIES = """org.gradle.jvmargs=-Xmx2G
org.gradle.daemon=false
"""

MCMOD_INFO = """[
  {
    "modid": "rottenleather",
    "name": "Rotten Flesh to Leather",
    "description": "Smelt rotten flesh into leather in a vanilla furnace. Fuel, experience, hoppers and all other furnace mechanics behave exactly as they do for a vanilla recipe.",
    "version": "${version}",
    "mcversion": "${mcversion}",
    "url": "https://github.com/tokyonocturn/rottenleather",
    "authorList": ["rottenleather"],
    "credits": "Uses the vanilla furnace so every other mod keeps working.",
    "logoFile": "",
    "screenshots": [],
    "parent": "",
    "dependencies": []
  }
]
"""

LANG_EN_US = """itemGroup.rottenleather=Rotten Flesh to Leather
rottenleather.command.status=Recipe status
rottenleather.command.reload=Reload the recipe from config
"""

VERSION_ENTRY = """package com.rottenleather.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;

/**
 * Forge entry point for Minecraft {mc}. Generated by tools/generate_forge.py.
 *
 * <p>The mod is server-friendly: {{@code acceptableRemoteVersions = "*"}} lets unmodified clients
 * join, while single-player and the integrated server load the same class locally.
 */
@Mod(modid = RottenLeatherMod.MOD_ID, name = RottenLeatherMod.NAME, version = RottenLeatherMod.VERSION,
        acceptedMinecraftVersions = "[{mc}]", acceptableRemoteVersions = "*", useMetadata = false)
public class VersionEntry {{

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {{
        RottenLeatherMod.preInit(event.getSuggestedConfigurationFile());
    }}

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {{
        RottenLeatherMod.loadComplete();
    }}

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {{
        RottenLeatherMod.serverStarting(event);
    }}

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {{
        RottenLeatherMod.serverStopping();
    }}
}}
"""

ADAPTER = """package com.rottenleather.forge;

import com.rottenleather.core.FurnaceTarget;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.world.World;
{chat_import}

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minecraft {mc} adapter. Generated by tools/generate_forge.py.
 *
 * <p>Only the APIs that moved inside 1.8-1.12.2 differ here: the item registry field name (1.8.x
 * uses {{@code itemRegistry}}, 1.9+ uses {{@code REGISTRY}}), the ItemStack count API
 * ({{@code stackSize}} before 1.11, {{@code getCount()}} from 1.11) and the chat component class
 * (1.11+). Furnace progress is read and written through the public furnace field API, which
 * reobfuscation maps for every supported version.
 */
public final class VersionAdapterImpl {{

    public static final String MC_VERSION = "{mc}";

    /**
     * 1.8.x furnaces always compare cookTime against a hard-coded 200 and expose no total-cook-time
     * field, so a non-vanilla smeltTimeTicks cannot be applied there. 1.9 added the total field.
     */
    public static final boolean SUPPORTS_CUSTOM_COOK_TIME = {supports_cook_time};

    /**
     * 1.8.x Forge refuses to register a smelting recipe for an input that already has one and its
     * map does not accept removal reliably, so conflictPolicy=replace is unavailable there and the
     * installer keeps the existing recipe instead.
     */
    public static final boolean SUPPORTS_RECIPE_REPLACE = {supports_replace};

    private VersionAdapterImpl() {{
    }}

    public static Item resolveItem(String id) {{
        if (id == null) {{
            return null;
        }}
        return Item.getByNameOrId(id);
    }}

    public static String idOf(ItemStack stack) {{
        if (isEmpty(stack) || stack.getItem() == null) {{
            return "minecraft:air";
        }}
        Object name = Item.{registry}.getNameForObject(stack.getItem());
        return name == null ? "minecraft:air" : name.toString();
    }}

    public static boolean isEmpty(ItemStack stack) {{
        return {empty};
    }}

    public static int countOf(ItemStack stack) {{
        return {count};
    }}

    public static boolean sameItem(ItemStack first, ItemStack second) {{
        if (isEmpty(first) || isEmpty(second)) {{
            return false;
        }}
        return first.getItem() == second.getItem() && first.getMetadata() == second.getMetadata();
    }}

    public static boolean sameItemAndCount(ItemStack first, ItemStack second) {{
        return sameItem(first, second) && countOf(first) == countOf(second);
    }}

    public static String describe(ItemStack stack) {{
        return isEmpty(stack) ? "minecraft:air" : idOf(stack) + " x" + countOf(stack);
    }}

    /** Chat components were renamed in 1.11; command replies go through this adapter. */
    public static void sendReply(ICommandSender sender, String message) {{
        sender.{chat_method}(new {chat_class}(message));
    }}

    /** Every loaded furnace in this world. Called only from the world tick handler. */
    public static List<FurnaceTarget> furnacesIn(World world) {{
        List<TileEntity> loaded = world.loadedTileEntityList;
        if (loaded == null || loaded.isEmpty()) {{
            return Collections.emptyList();
        }}
        List<FurnaceTarget> targets = new ArrayList<FurnaceTarget>();
        for (TileEntity tile : loaded) {{
            if (tile instanceof TileEntityFurnace) {{
                targets.add(new Target(world, (TileEntityFurnace) tile));
            }}
        }}
        return targets;
    }}

    private static final class Target implements FurnaceTarget {{

        private final TileEntityFurnace furnace;
        private final String where;

        Target(World world, TileEntityFurnace furnace) {{
            this.furnace = furnace;
            this.where = world.getWorldInfo().getWorldName() + "@"
                    + furnace.getPos().getX() + "," + furnace.getPos().getY() + "," + furnace.getPos().getZ();
        }}

        @Override
        public Object identity() {{
            return furnace;
        }}

        @Override
        public boolean inputIsRecipeInput() {{
            Item input = RottenLeatherMod.inputItem();
            ItemStack slot = furnace.getStackInSlot(0);
            return input != null && !isEmpty(slot) && slot.getItem() == input;
        }}

        @Override
        public int cookTime() {{
            // getField/setField are the public furnace API the vanilla GUI uses to sync progress
            // (0 burn time, 1 fuel burn time, 2 cook time, 3 total cook time). Reobfuscation maps
            // the call for every supported version, so no reflection or SRG names are involved.
            return furnace.getField(2);
        }}

        @Override
        public int totalCookTime() {{
            int value = furnace.getField(3);
            return value <= 0 ? 200 : value;
        }}

        @Override
        public void setTotalCookTime(int ticks) {{
            furnace.setField(3, ticks);
        }}

        @Override
        public String describe() {{
            return where;
        }}
    }}
}}
"""

COMMAND = """package com.rottenleather.forge;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
{command_imports}

import java.util.Collections;
import java.util.List;

/**
 * Minecraft {mc} command adapter. Generated by tools/generate_forge.py: 1.12 replaced
 * {{@code getCommandName/processCommand}} with {{@code getName/execute}}, and the body is shared.
 */
public class VersionCommand extends CommandBase {{

    @Override
    public String {name_method}() {{
        return "rottenleather";
    }}

    @Override
    public String {usage_method}(ICommandSender sender) {{
        return CommandSupport.usage();
    }}

    @Override
    public int getRequiredPermissionLevel() {{
        return 2;
    }}

    @Override
    public List<String> {alias_method}() {{
        return Collections.singletonList("rfl");
    }}

    @Override
    public void {exec_method}({exec_params}) {{
        VersionAdapterImpl.sendReply(sender, CommandSupport.execute(args));
    }}
}}
"""


def chat_for(mc):
    """Returns (fully-qualified chat component class, ICommandSender method) for this release."""
    if mc in ("1.8.8", "1.8.9"):
        # 1.8 keeps the pre-1.9 chat package and method names.
        return ("net.minecraft.util.ChatComponentText", "addChatMessage")
    if mc in ("1.9", "1.9.4"):
        # 1.9 moved chat components into net.minecraft.util.text; the sender method is unchanged.
        return ("net.minecraft.util.text.TextComponentString", "addChatMessage")
    # 1.10 renamed ICommandSender.addChatMessage to sendMessage.
    return ("net.minecraft.util.text.TextComponentString", "sendMessage")


def command_for(mc):
    """Returns the generated command's method names and imports."""
    if mc in ("1.11", "1.11.2", "1.12", "1.12.1", "1.12.2"):
        return {
            "name_method": "getName",
            "usage_method": "getUsage",
            "alias_method": "getAliases",
            "exec_method": "execute",
            "exec_params": "net.minecraft.server.MinecraftServer server, ICommandSender sender, String[] args",
            "command_imports": "",
        }
    if mc in ("1.9", "1.9.4"):
        # 1.9 replaced processCommand with execute(MinecraftServer, ...).
        return {
            "name_method": "getCommandName",
            "usage_method": "getCommandUsage",
            "alias_method": "getCommandAliases",
            "exec_method": "execute",
            "exec_params": "net.minecraft.server.MinecraftServer server, ICommandSender sender, String[] args",
            "command_imports": "",
        }
    if mc in ("1.10", "1.10.2"):
        # 1.10 renamed the command description methods and kept execute(MinecraftServer, ...).
        return {
            "name_method": "getName",
            "usage_method": "getUsage",
            "alias_method": "getAliases",
            "exec_method": "execute",
            "exec_params": "net.minecraft.server.MinecraftServer server, ICommandSender sender, String[] args",
            "command_imports": "",
        }
    return {
        "name_method": "getCommandName",
        "usage_method": "getCommandUsage",
        "alias_method": "getCommandAliases",
        "exec_method": "processCommand",
        "exec_params": "ICommandSender sender, String[] args",
        "command_imports": "",
    }


def write(path, content, check):
    current = path.read_text(encoding="utf-8") if path.exists() else None
    if current == content:
        return False
    if check:
        sys.stderr.write("stale: %s\n" % path.relative_to(ROOT))
        return True
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8", newline="\n")
    return True


def main(argv):
    check = "--check" in argv
    stale = False
    for mc, forge, fg, mappings, gradle, registry, modern in VERSIONS:
        base = ROOT / "forge" / "versions" / mc
        empty = ("stack == null || stack.isEmpty()" if modern
                 else "stack == null || stack.stackSize <= 0")
        count = "stack.getCount()" if modern else "stack.stackSize"
        chat_class, chat_method = chat_for(mc)

        stale |= write(base / "build.gradle",
                       BUILD_GRADLE.format(mc=mc, forge=forge, fg=fg, mappings=mappings), check)
        stale |= write(base / "gradle.properties", GRADLE_PROPERTIES, check)
        stale |= write(base / "src" / "main" / "resources" / "mcmod.info", MCMOD_INFO, check)
        stale |= write(base / "src" / "main" / "resources" / "assets" / "rottenleather" / "lang" / "en_us.lang",
                       LANG_EN_US, check)
        stale |= write(base / "src" / "main" / "java" / "com" / "rottenleather" / "forge" / "VersionEntry.java",
                       VERSION_ENTRY.format(mc=mc), check)
        stale |= write(base / "src" / "main" / "java" / "com" / "rottenleather" / "forge" / "VersionAdapterImpl.java",
                       ADAPTER.format(mc=mc, supports_cook_time=("false" if mc in ("1.8.8", "1.8.9", "1.9", "1.9.4", "1.10") else "true"),
                                      supports_replace=("false" if mc in ("1.8.8", "1.8.9") else "true"),
                                      registry=registry, empty=empty, count=count,
                                      chat_import="import %s;" % chat_class,
                                      chat_class=chat_class, chat_method=chat_method),
                       check)
        stale |= write(base / "src" / "main" / "java" / "com" / "rottenleather" / "forge" / "VersionCommand.java",
                       COMMAND.format(mc=mc, **command_for(mc)), check)

    manifest = ROOT / "forge" / "versions" / "versions.txt"
    stale |= write(manifest, "\n".join("%s %s %s %s" % (v[0], v[1], v[3], v[4]) for v in VERSIONS) + "\n", check)

    if check:
        raise SystemExit(1 if stale else 0)
    print("generated %d forge modules" % len(VERSIONS))


if __name__ == "__main__":
    main(sys.argv[1:])
