package com.raman.emergencyrelay.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * ============================================================
 * EMERGENCY RELAY — FIXED DESIGN PALETTE
 * ============================================================
 *
 * One built-in theme. The app does NOT follow the device
 * light/dark setting and does NOT use Material You dynamic
 * colour, so every user sees the same calm, high-contrast,
 * emergency-appropriate interface (Theme.kt enforces this).
 *
 * Role            Token                         HEX
 * --------------- ----------------------------- ---------
 * Primary         Teal                          #14B8A6
 * Secondary       Muted purple                  #8B5CF6
 * Success / go    Mint green                    #10B981
 * Warning         Amber                         #F59E0B
 * Alert / SOS     Coral                         #F43F5E
 * Neutral         Slate gray                    #64748B
 *
 * Reds are reserved for the SOS action and critical states,
 * so normal operation stays calm (per the design brief).
 */

// --- Brand / role colours ------------------------------------
val Teal          = Color(0xFF14B8A6)   // primary accent
val TealDeep      = Color(0xFF008080)   // darker teal (headers, pressed)
val Purple        = Color(0xFF8B5CF6)   // secondary accent
val Mint          = Color(0xFF10B981)   // success / delivered / "go"
val Amber         = Color(0xFFF59E0B)   // warning / searching / in-progress
val Coral         = Color(0xFFF43F5E)   // alert / SOS / critical
val Slate         = Color(0xFF64748B)   // neutral mid-tone, borders, disabled

// --- Surfaces (deep teal-black base) -------------------------
val BgBase        = Color(0xFF0E1A1C)   // app background
val Surface       = Color(0xFF13262A)   // cards / sheets
val SurfaceVariant = Color(0xFF152E31)  // washed card / list rows
val WheelWedge     = Color(0xFF214A4E)  // SOS wheel unselected wedge (brighter)
val Line          = Color(0xFF24403F)   // hairlines, outlines variant

// --- Text ----------------------------------------------------
val Ink           = Color(0xFFEAF4F3)   // primary text (on dark)
val Muted         = Color(0xFFD2E0DF)   // secondary text (brightened for contrast)
val InkOnTeal     = Color(0xFF04201E)   // text/icon on teal fills
val InkOnColor    = Color(0xFFFFFFFF)   // text on coral / amber / mint

// --- Containers (tinted fills) -------------------------------
val TealWash      = Color(0xFF143A38)   // primary container
val TealWashInk   = Color(0xFF9FEDE4)
val PurpleWash    = Color(0xFF231544)   // secondary container
val PurpleWashInk = Color(0xFFCFC0F6)
val MintWash      = Color(0xFF123324)   // success container
val MintWashInk   = Color(0xFFA7E8C9)
val CoralWash     = Color(0xFF3A1F24)   // error / alert container
val CoralWashInk  = Color(0xFFF6B4BF)
