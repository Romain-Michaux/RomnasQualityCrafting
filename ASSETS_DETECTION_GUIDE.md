# Assets Detection Guide

## What is Assets.zip?

Assets.zip is Hytale's core game assets file that contains all the JSON definitions for items, blocks, and other game content. RomnasQualityCrafting needs to read this file to generate quality variants of items.

## Default Detection Behavior

The mod automatically searches for Assets.zip in the following locations (in order):

1. **Custom config path** (if specified in `CustomAssetsPath`)
2. **Server root directory** (same folder as the `mods` folder)
3. **Hytale installation directory**: `Hytale/install/release/package/game/latest/Assets.zip`

## When Detection Fails

If the mod cannot find Assets.zip, you will see detailed error messages in the console explaining:
- All paths that were attempted
- How to fix the issue
- Multiple solutions you can try

## Solution 1: Configure Custom Path (Recommended)

This is the most reliable method if your Hytale installation is in a non-standard location.

### Steps:

1. Open your configuration file: `config/config.json`

2. Find the `CustomAssetsPath` field (should be empty by default)

3. Set it to the full path of your Assets.zip file or extracted assets folder

### Examples:

**Windows (ZIP file):**
```json
{
  "CustomAssetsPath": "C:/Hytale/install/release/package/game/latest/Assets.zip"
}
```

**Windows (extracted folder):**
```json
{
  "CustomAssetsPath": "C:/Hytale/HytaleAssets"
}
```

**Linux (ZIP file):**
```json
{
  "CustomAssetsPath": "/home/user/hytale/install/release/package/game/latest/Assets.zip"
}
```

**Linux (extracted folder):**
```json
{
  "CustomAssetsPath": "/home/user/hytale/HytaleAssets"
}
```

**Important Notes:**
- Use forward slashes (`/`) even on Windows
- Don't use backslashes (`\`) in the path
- Make sure there are no typos in the path
- The path must be absolute (full path from the drive root)

4. Save the file and restart your server

## Solution 2: Copy Assets.zip to Server Root

1. Locate Assets.zip in your Hytale installation:
   - Typical path: `Hytale/install/release/package/game/latest/Assets.zip`

2. Copy the file to your server's root directory (same folder as `mods`)

3. Restart the server

## Solution 3: Extract Assets.zip

1. Extract Assets.zip using any ZIP extraction tool

2. Rename the extracted folder to `HytaleAssets`

3. Place it in your server's root directory (same folder as `mods`)

4. Restart the server

## Verifying Success

When the mod successfully detects Assets.zip, you will see:
```
[RomnasQualityCrafting] JsonGenerator: === Starting Assets.zip Detection ===
[RomnasQualityCrafting] JsonGenerator: ✓ SUCCESS: Found Assets.zip at: [path]
```

## Troubleshooting

### Path not found
- Double-check spelling and capitalization
- Ensure the file/folder actually exists at that location
- Try using the full absolute path

### Permission denied
- Ensure the server has read permissions for the Assets.zip file
- On Linux, you may need to adjust file permissions: `chmod +r Assets.zip`

### Still not working?
- Check the server console for detailed error messages
- The mod will list all attempted paths - verify these are correct
- Try copying Assets.zip directly to the server root as a fallback

## Version Information

This asset detection system was added in version 1.2.0 to improve reliability and make troubleshooting easier.
