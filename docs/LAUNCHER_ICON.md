# Launcher icon update (0.21.6 / 28)

The previous lantern artwork update (f13c04e) existed only on the refactor branches.
The published main branch still contained the earlier icon, so pulling main could
not deliver the new artwork. This patch delivers the prepared lantern artwork on
main and is also applied to the current refactor branch.

The manifest explicitly uses ic_svetoch_lantern for both icon and roundIcon. Each
legacy density and the adaptive foreground have their own named lantern resource;
the adaptive background is white. Version code is 28, version name 0.21.6. The
prepared artwork is the previously disclosed image-derived version, not a new
regeneration or a claim of a byte-identical copy of the unavailable original.

Validation: assembleDebug passed; packaged manifest resolves icon/roundIcon to
the new adaptive resource; APK signature verified. Installed with pm install -r
on Android 28 (no application/data removal); a fresh launcher app-drawer screenshot
visually confirmed the new lantern. App identifier, database and runtime code are
unchanged. Existing resource files remain for compatibility; the manifest selects
the new resource names.

To receive the update in Android Studio, update the branch containing this patch
(main or the updated refactor branch), then Run the app. Confirm 0.21.6 / 28 in the
installed application's information. Updating the sources alone does not replace
an already installed APK. If a particular launcher still caches its old shortcut,
restart that launcher/device or recreate that shortcut; clearing application data
or uninstalling Svetoch is unnecessary.
