# MIDI Timeline and Piano Roll Documentation

## Overview

The MIDI Timeline and Piano Roll features provide Digital Audio Workstation (DAW) functionality within Amethyst's unified Timeline. This implementation allows users to create, edit, and play back MIDI sequences alongside audio tracks.

## Architecture

### Data Models

#### MidiNote
Represents a single MIDI note with the following properties:
- `pitch` (0-127): MIDI note number where 60 is middle C
- `velocity` (0-127): Note velocity (how hard the note is played)
- `startTimeMs`: Start time relative to the parent entry in milliseconds
- `durationMs`: Duration of the note in milliseconds

#### MidiEntry
A timeline entry containing multiple MIDI notes:
- `startTimeMs`: Start time on the timeline in milliseconds
- `durationMs`: Duration of the entire entry
- `notes`: List of MidiNote objects
- `name`: Display name for the entry

#### MidiTimelineTrack
A timeline track that contains MIDI entries

### UI Components

#### PianoRollView
Full piano roll interface for visualizing and editing MIDI notes

#### MidiClip
Timeline representation of a MIDI entry

### Playback System

The MIDI playback system is integrated into TimelineRepository and provides:
1. Synchronized Playback with audio tracks
2. Real-time note processing
3. Seek support
4. Note state management

## Usage Examples

See source code for detailed API usage.

## MIDI File Format Support

Supports standard MIDI files with note on/off events, pitch, velocity, and timing.
