# TwinSpace

An original, from-scratch Android app cloner. TwinSpace creates an isolated **Work Profile** and lets you run a second copy of any app — separate account, separate data — with **no root**.

## Features
- One-tap setup of a managed Work Profile (Device Policy Controller)
- Clone any installed app into the work space
- Open, freeze/unfreeze, and uninstall cloned apps
- Clean Material 3 interface

## Build
Built automatically by GitHub Actions on every push (see `.github/workflows/build.yml`). The debug APK is attached as a workflow artifact.

## Notes
- This is a v1 MVP.
- Only uses the public Android Work Profile / DevicePolicyManager APIs; no All-Files access.
