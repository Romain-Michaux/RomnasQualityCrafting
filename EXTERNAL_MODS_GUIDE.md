# External Mods Detection Guide

This guide explains how RomnasQualityCrafting detects and integrates with external mods installed on your server.

## Overview

RomnasQualityCrafting automatically scans external mods to find weapons, armor, and tools, then generates quality variants for these items. This allows modded items to work seamlessly with the quality system.

## How Detection Works

### 1. Global Mods Directory Detection

The mod needs to find your **global Mods directory** where Hytale stores installed mods. This is different from your server's save-specific mods.

#### Automatic Detection

RomnasQualityCrafting automatically detects the Mods directory based on your operating system:

**Windows:**
- `C:\Users\YourName\AppData\Roaming\Hytale\UserData\Mods` (primary)
- `C:\Users\YourName\AppData\Local\Hytale\UserData\Mods` (fallback)

**Linux:**
- `~/.local/share/Hytale/UserData/Mods` (primary)
- `~/.hytale/UserData/Mods` (fallback)

**macOS:**
- `~/Library/Application Support/Hytale/UserData/Mods` (primary)
- `~/.hytale/UserData/Mods` (fallback)

**Dedicated Servers:**
- `./Mods` (relative to server directory)
- `./mods`
- `./UserData/Mods`

#### Manual Configuration

If automatic detection fails or you have a non-standard setup, specify the path manually in your config:

```json
{
  "CustomGlobalModsPath": "C:/Users/YourName/AppData/Roaming/Hytale/UserData/Mods"
}
```

**Examples for different systems:**

```json
// Windows
"CustomGlobalModsPath": "C:/Users/YourName/AppData/Roaming/Hytale/UserData/Mods"

// Linux
"CustomGlobalModsPath": "/home/username/.local/share/Hytale/UserData/Mods"

// macOS
"CustomGlobalModsPath": "/Users/username/Library/Application Support/Hytale/UserData/Mods"

// Dedicated Server
"CustomGlobalModsPath": "D:/HytaleServer/Mods"
```

### 2. Mod Scanning Process

Once the Mods directory is found, RomnasQualityCrafting:

1. **Lists all mods** in the directory (both folders and ZIP/JAR files)
2. **Checks if each mod is loaded** on the server
3. **Validates mod structure** (must contain `Server/Item/Items/`)
4. **Scans for weapons, armor, and tools** only
5. **Extracts item definitions** from JSON files
6. **Generates quality variants** for compatible items

### 3. Mod Name Matching

The mod uses smart name matching to determine which mods are loaded:

**Supported filename patterns:**
- `ModName` (simple name)
- `ModName_v1.2.3` (with version prefix)
- `ModName-1.2.3` (with dash separator)
- `ModName1.2.3` (version directly attached)
- `ModName.zip` or `ModName.jar` (compressed)

**Examples:**
- `Wans_Wonder_Weapon` → matches mod name "Wans_Wonder_Weapon"
- `Wans_Wonder_Weapon_v1.0.6` → matches mod name "Wans_Wonder_Weapon"
- `AwesomeMod-1.2.3.zip` → matches mod name "AwesomeMod"

## Understanding the Logs

### Startup Logs

When the server starts, you'll see detailed information about mod detection:

```
═══════════════════════════════════════════════════════════════════
║  STARTING GLOBAL MODS DIRECTORY DETECTION                      ║
═══════════════════════════════════════════════════════════════════

No custom global mods path configured (CustomGlobalModsPath is empty)

✓ SUCCESS: Found global mods directory at: C:\Users\YourName\AppData\Roaming\Hytale\UserData\Mods

═══════════════════════════════════════════════════════════════════
║  SCANNING EXTERNAL MODS FOR ITEMS                              ║
═══════════════════════════════════════════════════════════════════

Detected 2 loaded mod(s): [ModA, ModB]

Scanning global mods directory: C:\Users\YourName\AppData\Roaming\Hytale\UserData\Mods

Found 5 element(s) in global mods directory

Processing ZIP/JAR mod: ModA_v1.0.6.zip
  → Added 15 item(s) from this mod

Processing directory mod: ModB
  → Added 8 item(s) from this mod

Processing ZIP/JAR mod: ModC.jar
  → No items found or mod not loaded

═══════════════════════════════════════════════════════════════════
║  EXTERNAL MODS SCAN COMPLETE                                   ║
═══════════════════════════════════════════════════════════════════
Mods scanned:      5
Mods processed:    2
Mods skipped:      3
Total items found: 23
Unique items:      23
═══════════════════════════════════════════════════════════════════
```

### What Each Section Means

**Directory Detection:**
- Shows whether custom path is configured
- Lists all attempted paths with ✓ (success) or ✗ (failure)
- Reports final result

**Mod Scanning:**
- Shows number of loaded mods detected
- Lists each mod being processed
- Reports items found per mod
- Shows final statistics

**Statistics:**
- **Mods scanned**: Total number of files/folders checked
- **Mods processed**: Mods that were loaded and had items
- **Mods skipped**: Mods that were not loaded or had no valid structure
- **Total items found**: Total items detected
- **Unique items**: Number of distinct items registered

## Troubleshooting

### No Mods Detected

**Problem:** The log shows "No global mods directory found"

**Solutions:**

1. **Use Custom Path**
   - Find your Hytale Mods directory manually
   - Add it to your config using `CustomGlobalModsPath`
   - Restart the server

2. **Check Default Paths**
   - Verify Hytale is installed
   - Check that UserData/Mods directory exists
   - Ensure proper permissions

3. **Verify Mods Directory**
   - Make sure the path points to the global Mods directory
   - Not your server's world-specific mods directory
   - Should contain your installed mod files

### Mods Found But Not Processed

**Problem:** The log shows "Mods skipped: X"

**Possible Causes:**

1. **Mod Not Loaded**
   - The mod is in the directory but not loaded on this server
   - Check your server's mod configuration
   - Verify the mod is enabled for your save

2. **Invalid Structure**
   - The mod doesn't have a `Server/Item/Items/` directory
   - This is normal for non-content mods (libraries, utilities)
   - Only mods with items are processed

3. **No Compatible Items**
   - The mod has items but no weapons/armor/tools
   - RomnasQualityCrafting only processes equipment items
   - Decorative items and consumables are skipped

### Name Matching Issues

**Problem:** Mod is loaded but not detected

**Solutions:**

1. **Check Verbose Logs**
   - Enable verbose logging in config: `"VerboseLogging": true`
   - Look for "Detected X loaded mod(s)" message
   - Compare loaded names with file names

2. **Rename Mod File**
   - Ensure mod filename matches the mod's internal name
   - Remove complex version strings if needed
   - Use simple names like `ModName.zip`

3. **Report Issue**
   - If a mod consistently fails detection
   - Share the mod name and filename in a bug report
   - Include relevant log sections

## Performance Considerations

### Scan Duration

Mod scanning happens once at server startup:
- **Small setups** (1-5 mods): < 1 second
- **Medium setups** (5-20 mods): 1-3 seconds
- **Large setups** (20+ mods): 3-10 seconds

### Memory Usage

Each scanned mod adds minimal memory overhead:
- Item definitions are stored once
- JSON files are read only during scan
- No persistent ZIP file handles

### Optimization Tips

1. **Keep Only Loaded Mods**
   - Remove unused mods from the Mods directory
   - Reduces scan time
   - Improves clarity in logs

2. **Use Directory Mods**
   - Extracted mods (directories) scan faster than ZIPs
   - No decompression overhead
   - Easier to debug

3. **Disable Verbose Logging**
   - Set `"VerboseLogging": false` after setup
   - Reduces log spam
   - Improves startup time slightly

## Advanced Configuration

### Example Full Configuration

```json
{
  "QualityEnabled": true,
  "VerboseLogging": false,
  "CustomAssetsPath": "",
  "CustomGlobalModsPath": "C:/Users/YourName/AppData/Roaming/Hytale/UserData/Mods",
  "ExcludedItems": [
    "hytale:wooden_sword",
    "hytale:stone_pickaxe"
  ]
}
```

### Multiple Server Instances

If you run multiple servers with different mod configurations:

1. **Use separate configs** for each server
2. **Point to same Mods directory** (they'll filter by loaded mods)
3. **Or use separate Mods directories** with different `CustomGlobalModsPath`

### Dedicated Server Setup

For headless dedicated servers without a full Hytale installation:

1. **Create a Mods directory** in your server folder
2. **Copy required mods** into it
3. **Set CustomGlobalModsPath** to point to it:
   ```json
   "CustomGlobalModsPath": "./Mods"
   ```

## FAQ

**Q: Does this work with client-only mods?**
A: No, only server-side content mods with item definitions are supported.

**Q: Can I exclude specific mods from scanning?**
A: Not directly, but you can exclude specific items using `ExcludedItems` in the config.

**Q: Will this work when Hytale updates?**
A: As long as the mod structure remains compatible, yes. We'll update as needed.

**Q: Does this affect mod load order?**
A: No, RomnasQualityCrafting only reads mod assets after mods are already loaded.

**Q: Can I use this with modpacks?**
A: Yes! It will scan all mods in the Mods directory and process loaded ones.

## Support

If you encounter issues with external mod detection:

1. **Check server logs** for error messages
2. **Enable verbose logging** for detailed diagnostics
3. **Verify paths** in your configuration
4. **Report bugs** with log excerpts and mod list

For more help, visit the mod's GitHub page or Discord server.
