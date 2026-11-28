# Native tablet app: UI upgrade

New files
- app/src/main/java/.../ui/components/ToolIcons.kt        custom icons: ballpoint, fountain nib, highlighter, eraser, lasso, magic wand, shapes, text
- app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml   new adaptive launcher icon

Edited files
- ui/components/StylusToolbar.kt   uses ToolIcons; tool buttons now animate (colour fade + springy select)
- ui/components/PageNavigator.kt   "New Page" button removed
- ui/components/NoteTabBar.kt      top-bar "+ Page" button removed
- ui/screens/MainTabletScreen.kt   page navigator only shows while scrolling / changing page (fades after ~1.8 s);
                                   end-of-notebook "Add page" is now a quiet outlined button
- ui/theme/Color.kt, Theme.kt      refined indigo-blue palette, cooler canvas, new dark palette, rounded shapes
- res/mipmap-anydpi-v26/ic_launcher*.xml   point to the new icon (incl. Android 13 themed icon)

Not compiled here (no Android SDK in my sandbox). Kotlin syntax of every changed file was checked with kotlinc,
and ToolIcons.kt was type-checked against Compose API stand-ins. If the GitHub Actions build reports an error, send me the log.
