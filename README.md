# Tab Groups


Organize your editor tabs into colored groups for better file navigation and focus.

<!-- Plugin description -->
Working with large projects often means managing many open files. **Tab Groups** helps you organize related files with custom names and colors, making it easier to navigate and maintain focus.

**Key Features:**
- 🎨 Create named tab groups with custom colors
- 🎯 **Colored editor tabs** for visual file identification
- 🌳 Git branch-aware groups (different groups per branch)
- 📌 **Pin frequently-used groups** for persistent access
- 📝 Dedicated tool window for group management

<!-- Plugin description end -->

## Overview

### Pin Frequently-Used Files for Quick Access

**Pin groups** containing your most-accessed files—they'll appear at the top of the tool window and persist across all Git branches. This keeps your frequently-used files readily available regardless of which branch you're on.

```
Pinned Groups (Always Visible)
├── 📌 Core Utils (Purple) ← Your most-used utilities
└── 📌 API Client (Blue) ← Your main service layer

Branch-Specific Groups
├── Feature: Auth (Green) ← Current branch only
└── UI Components (Orange) ← Current branch only
```

### Colored Tabs for Visual Recognition

Each group gets a color that appears on editor tabs. This makes file categorization visible at a glance. For example:

- **Blue tabs** → Utilities
- **Green tabs** → Services
- **Orange tabs** → UI Components
- **Purple tabs** → Configuration

With colored tabs, you can identify file categories quickly without reading names.

## Getting Started

### Add Files to a Group

1. **Right-click on any editor tab** (or file in Project View)
2. Select **"Add to Group..."**
3. Choose an existing group or create a new one
4. The tab now displays the group's color

### Manage Groups

Use the **Tab Groups** tool window (View → Tool Windows → Tab Groups) to:
- View all groups and their files
- Create, rename, and delete groups
- Change group colors
- Pin/unpin groups across branches

## 💡 Features Explained

### Named Groups with Custom Colors

Organize your files logically. For example:
- **UI Components** (Blue) - All React/Compose components
- **API Services** (Green) - All backend service calls
- **Tests** (Orange) - Test files
- **Config** (Purple) - Configuration files

Each group's color appears **directly on editor tabs** for consistent visual organization.

### Pin Groups for Always-Visible Access

Mark your most-used groups as **pinned** to:
- Keep them at the top of the tool window, always visible
- Access them instantly regardless of current branch
- Never lose track of your core utilities or frequently-opened files
- Perfect for marking:
  - API client services
  - Utility functions
  - Core configuration files
  - Common data models

**Example:** Pin your **"API Client"** group so it's always accessible, even when working on different feature branches.

### Colored Editor Tabs for Navigation

Tabs in the editor display their group's color, which helps with:
- **Visual organization** - Files are identifiable by color
- **Visual consistency** - Group organization appears in tabs and the tool window
- **Tab bar clarity** - Grouping is visible at a glance

This is especially useful when working with many open files.

### Git Branch Awareness

Each Git branch maintains its own set of groups. When you switch branches:
- Group organization updates automatically
- **Pinned groups appear across all branches** for continuity
- Perfect for context-switching between features
- Each branch can have its own working groups

### File Management

The plugin automatically handles:
- ✅ File renames and moves
- ✅ File deletion cleanup
- ✅ Project refactoring

## 📦 Installation

### From Marketplace (Recommended)

1. Open **Settings/Preferences** → **Plugins** → **Marketplace**
2. Search for **"Tab Groups"**
3. Click **Install**
4. Restart your editor

### Manual Installation

1. Download the [latest release](https://github.com/PopovYuriy/tabs_group_plugin/releases)
2. Go to **Settings/Preferences** → **Plugins** → **⚙️** → **Install plugin from disk...**
3. Select the downloaded `.zip` file
4. Restart your editor

## 🎮 Usage Examples

### Example 1: Organizing a Multi-Module Project

```
Frontend Project
├── 📦 UI Components (Blue)
│   ├── Button.kt
│   ├── Dialog.kt
│   └── Header.kt
├── 📦 Pages (Green)
│   ├── HomePage.kt
│   ├── SettingsPage.kt
│   └── ProfilePage.kt
└── 📦 Utils (Purple)
    ├── ApiClient.kt
    └── DateFormatter.kt
```

Right-click any file and add it to its logical group. Colors appear immediately in tabs and the tool window.

### Example 2: Pin Core Files for Quick Access

Keep frequently-used files accessible:

1. Create a **"Core Utils"** group (Green) with:
  - `ApiClient.kt`
  - `DateFormatter.kt`
  - `Logger.kt`
2. **Pin the group** (right-click → "Pin")
3. The group stays at the top across all branches
4. Core utilities are available when switching between branches

### Example 3: Feature Branch Development

Working on a feature with related files:

1. Create a **"Feature: Auth"** group (Purple)
2. Add authentication-related files
3. **Right-click → "Pin"** to keep it visible across branches
4. When you return to this branch, your pinned group remains

### Example 4: Test-Driven Development

Organize implementation and test files together:

1. Create **"Tests"** group (Orange) with test files
2. Create **"Impl"** group (Blue) with implementation files
3. Open a test → implementation files auto-close
4. Purple and blue tabs identify file purpose
5. Navigate between test and implementation using tab colors

## Visual Organization

### Tab Bar Clarity

Colored tabs help organize your tab bar visually:

**Without grouping:**
```
├─ ApiClient.kt
├─ Button.kt
├─ Logger.kt
├─ Dialog.kt
├─ DateFormatter.kt
├─ Header.kt
└─ Config.kt
```

**With Tab Groups:**
```
├─ ApiClient.kt (🟢 Green)
├─ Button.kt (🔵 Blue)
├─ Logger.kt (🟢 Green)
├─ Dialog.kt (🔵 Blue)
├─ DateFormatter.kt (🟢 Green)
├─ Header.kt (🔵 Blue)
└─ Config.kt (🟣 Purple)
```

### Combining Pinning and Colors

Use **pinned groups** with **colored tabs** together:

1. **Pin** your frequently-accessed groups
2. Their **colors appear in the tab bar**
3. **Find files** by recognizing their group color

This approach is helpful when working with multiple files across different parts of your codebase.

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built with the [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/)
- Plugin template based on [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- Icons from JetBrains built-in icon library

## 📧 Support & Feedback

- **Issues**: [GitHub Issues](https://github.com/PopovYuriy/tabs_group_plugin/issues)
- **Discussions**: [GitHub Discussions](https://github.com/PopovYuriy/tabs_group_plugin/discussions)
- **Contact**: [popov.yuriy.92@gmail.com](mailto:popov.yuriy.92@gmail.com)

---

**Made with ❤️ by [Popov Yurii](https://github.com/PopovYuriy)**

If you find Tab Groups useful, consider giving it a ⭐ on GitHub.