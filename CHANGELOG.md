<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# tabs_group_plugin Changelog

## [Unreleased]
### Fixed
- A long branch name is truncated with an ellipsis in the tool window header instead of running
  off the edge, with the full name shown on hover.

## [1.1.0]
### Added
- Multi-file selection support for **Add to Group** in the Project View.
- Directory renames and moves now re-point every grouped file underneath them.
- The tool window shows the branch the current groups belong to.
- Unit tests for the storage layer (ordering, pinning, path rewriting).

### Changed
- File paths are stored relative to the project root, so groups survive the project being moved,
  re-cloned or opened on another machine.
- Group colors are stored by preset name and resolve per theme, instead of a fixed RGB value.
- Editor tab tints are blended against the editor background rather than drawn with an alpha
  channel, which renders consistently across themes and IDE versions.
- Branch detection reads `.git/HEAD` on the shared scheduler, gated on the file's modification
  stamp. The plugin no longer references Git4Idea and installs in any JetBrains IDE.
- `TabGroup` is immutable; every change goes through `TabGroupService`.
- Logging moved from `println` to the platform logger.
- Dialogs use `Messages` instead of `JOptionPane`, so they are themed and correctly modal.

### Fixed
- Reading groups for a branch no longer creates an empty entry for it, which used to add a row to
  `tabGroups.xml` for every branch ever checked out.
- **Move Up** / **Move Down** are correctly enabled: pinned and branch groups are separate
  sections and reorder independently.
- Pinning a group no longer inserts it at an arbitrary position in the pinned list.
- A branch literally named `default` no longer shares storage with the no-branch bucket.
- The pinned flag can no longer disagree with the list a group actually lives in.
- The VFS listener no longer starts a thread per created file; a branch checkout used to spawn
  thousands of sleeping threads.
- The VFS message bus connection, the tool window's change listener and the branch poller are all
  released with the project.
- The tool window keeps its scroll position across refreshes and coalesces rapid updates.
- Tab color lookup is a single hash lookup instead of a full rebuild-and-scan on every repaint.
- `plugin.xml` no longer hardcodes a version and build range that contradicted `gradle.properties`,
  and no longer registers each service twice.

## [1.0.0]
### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)