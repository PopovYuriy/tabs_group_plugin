<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Tab Groups Changelog

## [Unreleased]
### Added
- Named tab groups, each with a color from a six-preset palette that adapts to light and dark
  themes.
- **Add to Group** in the editor tab and Project View context menus, with multi-file selection.
- Editor tabs tinted with their group's color.
- Tool window listing every group: expand and collapse, reorder groups and files, sort a group's
  files by extension and name, rename, recolor and delete.
- Groups are scoped to the current Git branch, so switching branches switches the working set.
- Pinned groups stay visible on every branch.
- File paths are stored relative to the project root, so groups survive the project being moved,
  re-cloned or opened on another machine.
- Renaming, moving or deleting a file or a directory updates the groups that reference it.
- Runs in any JetBrains IDE: the plugin depends only on the platform, and detects the current
  branch by reading `.git/HEAD` rather than through the Git plugin.