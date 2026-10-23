# Essential Remapper UI reference

Source: [Nothing won't fix their button, so I did... by AllexB](https://www.youtube.com/watch?v=YhY94x3HL7o).

This is a UI design specification and feature inventory, not an app implementation. Visual observations come from the video; behavior comes from its transcript. Exact source fonts, color tokens, measurements, and animation curves cannot be recovered reliably from the recording.

## Visual style

- Light, near-white backgrounds with subtle gray surfaces.
- Dark text and small monochrome line icons.
- Rounded panels, pill-shaped segmented controls, and rounded switches.
- Mostly left-aligned content, with generous space around page titles.
- Settings rows combine a leading icon, title, smaller supporting text, and a trailing switch, value, or navigation chevron.
- Restrained use of accent color. The recording shows blue enabled switches and a configurable overlay color.
- Material You / Material 3 styling, explicitly described by the creator.
- Nothing-inspired dot-matrix typography gives headings their distinctive appearance.

## Typography

| Role | Reference treatment | What is known |
| --- | --- | --- |
| Page titles and display text | Dot-matrix / dotted display lettering | The app detects and uses the Nothing font already installed on the phone. |
| Alternative display font | Similar dot-matrix appearance | The creator describes an open-source fallback but does not name it. |
| Labels and body text | Clean, readable Android sans-serif | Exact family and weights are unverified. |
| Supporting descriptions | Smaller, quieter sans-serif text | Exact size, line height, and color are unverified. |

The creator says the proprietary Nothing font is not bundled with the app. For a later recreation, use the device font where available or choose a licensed alternative. Any proposed substitute should be labeled as a substitute.

## Screens and flows

### Setup

1. Detect the device and let the user manually select a Nothing / CMF model or an unrecognized model.
2. Disable Essential Space through the built-in pairing flow, Shizuku, or manual ADB.
3. Request accessibility access to detect button presses.
4. Request exemption from battery optimization.
5. Ask the user to press the Essential button to identify it.
6. Choose initial haptic and visual overlay preferences.

The built-in flow includes developer options, wireless debugging, and entering the pairing code through a notification. It requires a Wi-Fi connection.

### Home / gesture mappings

- App title with access to settings.
- Three configurable mappings: single press, double press, and long press.
- Each mapping opens an action picker.
- Show the current action so users can review their configuration.

### Action picker

- Rounded bottom sheet with a drag handle, heading, grouped actions, and leading icons.
- Quick actions include flashlight and rotation lock.
- Media actions include playback controls.
- Launch an installed app.
- Launch an app shortcut or deep link into a specific app screen.

### Settings

- Appearance and typography preferences.
- Device selection and overlay positioning.
- Gesture timing and lock-screen behavior.
- Haptic feedback settings.
- Visual overlay settings.
- Backup and restore.
- A way to re-enable Essential Space.

### Appearance

- Light, dark, and automatic theme selection are visible in the recording.
- Dotted font preference and a fallback-font option are shown.
- Use clear previews or immediate feedback when changing appearance.

### Visual overlay

- Two selectable styles: Nothing-inspired and stock Android volume-popup style.
- Style preview tiles.
- Configurable color and glow.
- An animated indicator beside the physical Essential button.
- State-aware icons, such as flashlight on/off and rotation locked/unlocked.
- Per-device positioning and a manual adjustment for unrecognized models.
- Position remains anchored to the physical button when the screen rotates.

### Gestures and haptics

- Adjust the double-press interval.
- Adjust the long-press duration.
- Enable lock-screen use separately for each gesture.
- Lock-screen gestures are disabled by default to reduce accidental presses.
- Toggle haptic feedback and choose its intensity, with an immediate sample.

### Backup and restore

- Export configuration to a JSON file.
- Import configuration from a JSON file.
- Carry settings to another supported phone.

## Component inventory

- Page header with back navigation where needed.
- Settings icon button.
- Gesture mapping controls.
- Settings rows with supporting descriptions.
- Switches.
- Theme segmented control.
- Bottom sheet action picker.
- Overlay style preview tiles.
- Color selector.
- Timing and positioning adjustment controls. Exact control types should be checked in a closer reference before implementation.
- Permission/setup steps with status and action buttons.
- Import and export actions.

## Motion and feedback

- Material predictive-back animations, explicitly mentioned in the video.
- Animated overlay in response to button presses.
- Icon changes that reflect the action's current state.
- Optional glow and haptic feedback.
- Exact durations and easing curves are unverified.

## Functional constraints to preserve

- Standard remapping requires disabling Essential Space. The creator says simultaneous operation would require a more invasive approach such as root or an LSPosed module.
- Essential Space can be restored later.
- A button press can briefly wake the screen even when lock-screen actions are disabled.
- Support targets Nothing and CMF devices with an Essential button. Other Android phones are not guaranteed to work.
- The creator states that accessibility access only detects button presses, and that the app has no analytics or tracking. These are developer claims, not an independent audit.
- Low battery use is a developer claim; the video does not provide a controlled battery benchmark.

## Reference chapters

- [Setup completion, 4:46](https://www.youtube.com/watch?v=YhY94x3HL7o&t=286s)
- [Main UI and typography, 5:41](https://www.youtube.com/watch?v=YhY94x3HL7o&t=341s)
- [Remapping actions, 6:11](https://www.youtube.com/watch?v=YhY94x3HL7o&t=371s)
- [Visual overlay, 6:44](https://www.youtube.com/watch?v=YhY94x3HL7o&t=404s)
- [Customization, 8:13](https://www.youtube.com/watch?v=YhY94x3HL7o&t=493s)
- [Backup and restore, 9:05](https://www.youtube.com/watch?v=YhY94x3HL7o&t=545s)
