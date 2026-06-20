# Screenshots

Images used by the top-level [`README.md`](../../README.md). The `bind-control` and `lighting` shots
are captured at 1470 px wide; `main-window` uses a tighter 1120×720 viewport so the device just fits.

| File | Shows | Used in README |
|------|-------|----------------|
| `main-window.png` | The main configuration window — the device's knobs/sliders/buttons laid out, with the left sidebar listing devices and live integrations. | Hero image near the top. |
| `bind-control.png` | Configuring a knob: an action bound with its input-mapping curve, per-control color, and the categorized action-type menu (audio / system / integrations). | "What it does" — binding & breadth of actions. |
| `lighting.png` | The Lighting view: RGB modes (solid, rainbow, wave, breath, per-control) with a live device preview. | "What it does" — RGB. |

## Recapturing

The screenshots come straight from the running app's web UI at `http://localhost:7654/` with a
1470×900 browser viewport. Start the app (or `./mvnw quarkus:dev`), open that URL, and capture the
main view, a control's configuration panel (click a knob), and the Lighting view.

Tips: use a profile with recognizable apps/integrations so the per-app and integration story is
obvious; trim to the window; keep file sizes reasonable.

## Possible future additions

- `hero.png` — a photo of the PCPanel hardware next to the app window, to make the "physical knobs ↔
  on-screen controls" idea immediate.
- An on-screen **volume overlay** shot. The overlay is a native Win32 window, so it can't be captured
  from the web UI — it needs a desktop screen grab while turning a knob.
</content>
