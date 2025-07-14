Combat Initiative Tracker - Complete Requirements Document
1. Project Overview
1.1 Project Name
Combat Initiative Tracker for Android
1.2 Purpose
Develop an Android application that replicates the combat initiative tracking functionality from Baldur's Gate 3, allowing users to manage turn-based combat encounters with customizable actors, initiative tracking, and status effect management.
1.3 Target Platform
•	Platform: Android
•	Minimum API Level: Flexible; prioritizing ease of development on current Android system. Compatibility with API 28 (Android 9.0) is a preference, but the primary goal is ease of development, willing to compromise on API 28 support if it significantly increases development complexity.
•	Target API Level: Latest stable Android API at time of development
•	Language: Kotlin
•	Architecture: MVVM or similar modern Android architecture
•	Orientation: Landscape only
2. Application Structure
2.1 Main Screen
Grid layout with large buttons/cards for:
•	Manage Encounters: Load or delete saved encounters
•	Create New Encounter: Set up new combat encounter
•	Manage Actors: Create, edit, delete actor library
•	Settings: App configuration and reset options
•	Close App: Exit application
2.2 Screen Navigation Flow
Main Screen
├── Manage Encounters → Encounter List → Load/Delete
├── Create New Encounter → Set Name (dedicated screen in paginated flow) → Select Actors → Save/Start
├── Manage Actors → Actor Library → Add/Edit/Delete Actors
├── Settings → Background, Reset Options
└── Encounter Screen → Active Combat Tracking
3. Functional Requirements
3.1 Actor Management
3.1.1 Actor Data Structure
•	Name: Text field (required)
•	Portrait: Image from device storage (optional, uses standard Android photo picker)
•	Initiative Modifier: Numeric value (integer, can be negative)
•	Category: One of four types - Players, NPCs, Monsters, Others
•	Status Effects: Collection of applied conditions with durations
3.1.2 Actor Creation and Editing
•	Users can create new actors with all required fields
•	Users can edit existing actors (change picture, name, category, initiative modifier)
•	Users can delete actors from their library
•	Actors are stored locally on the device for reuse across sessions
•	Actor portraits copied to app's internal storage when selected
•	Default actor pictures bundled in app resources per category
3.1.3 Actor Library Management (Manage Actors Screen)
•	Display: Actor name, thumbnail picture, and actor category
•	Search: Text-based search within actor names
•	Sort: Alphabetical sorting by name, category, or initiative modifier
•	Filter: Filter by actor category (Players, NPCs, Monsters, Others)
•	Categories: Organizational tool only, no behavioral differences
•	Actions: Add new actor, edit existing actor, delete actor
3.1.4 Multiple Actor Instances
•	When adding multiple instances of the same actor to an encounter, unique numbers appended to names (e.g., "Goblin 1", "Goblin 2")
•	Counter persists throughout encounter; removal doesn't reset counter for new additions
•	The system tracks the highest counter used within that specific encounter
•	If an actor (e.g., "Goblin 1") is removed, and a new unnumbered 'Goblin' is added, it will receive the next available number (e.g., "Goblin 3")
•	Counters reset to 1 for each new encounter
•	System remembers removed actors, so re-adding continues counter sequence
•	Actor Library Structure: The actor library will only contain base actor models (e.g., "Goblin"), not numbered instances. Numbering is applied exclusively to actor instances within an active encounter.
3.2 Portrait and Background Management
3.2.1 Image Support
•	Supported Formats: Common formats (JPG, PNG, GIF, WebP)
•	Source: Standard Android photo picker/gallery
•	Aspect Ratio: All actor portraits standardized to 1:1.5 ratio (portrait orientation)
•	Scaling: Images scaled and cropped to fit standardized portrait dimensions
•	Placeholder Images: Default placeholder per actor category when no custom image provided
•	Name Display: Actor names shown underneath placeholder portraits
3.2.2 Background Customization
•	Users can set custom background image via Settings screen
•	Background selected via Android file picker
•	New background overwrites old in app storage
•	Image scaled horizontally, vertical overflow cropped
•	Background fills entire screen behind the tracker
•	Default: Black background if no picture set
3.3 Encounter Management
3.3.1 Encounter Creation Workflow
1.	Set Encounter Name: Optional field; if not provided, uses format "ENCsave_YYYYMMDD_HHMMSS" using the date and time of when the encounter is initially created
2.	Select Actors: 
o	Display all actors with name, thumbnail, and category
o	Use checkboxes for selection
o	Optional number input field for multiple instances of same actor
3.	Save or Start: Option to save encounter or start immediately
3.3.2 Encounter List Management
•	Manage Encounters Screen: Shows detailed list with encounter name + date/time + actor count
•	Load Encounter: Immediately starts selected encounter
•	Delete Encounter: Single encounter deletion with confirmation
3.3.3 Initiative System
•	Rolling Options: 
o	Roll initiative for all actors (d20 + individual modifier)
o	Roll initiative for NPCs only (NPCs, Monsters, Others)
o	Manual initiative setting for individual actors
•	Initiative Calculation: (d20 roll + modifier) + (random decimal between 0.0 and -0.2) for NPCs
•	Tie Breaking System: 
o	NPCs (NPCs, Monsters, Others): Automatic tie-breaking with random decimal values (0.0000 to -0.1999), initiative supports decimals
o	Player Characters: Initiative remains integers; tied players all displayed enlarged, manual re-sorting required via context menu
o	Default Sorting for Tied PCs: By category (Players, then NPCs, Monsters, Others), then alphabetically by name
•	Initiative Display: Values accessible only via expandable context menu per actor
•	Initiative Editing: Manual modification during encounter via context menu
•	Dynamic Reordering: Actors automatically reposition when initiative values change
•	Missing Initiative: 
o	Actors without initiative highlighted with red overlay
o	Same size as inactive actors
o	Actor names continue to be shown underneath portraits
o	ALL "Next Turn", "Previous Turn", "Next Round", and "Previous Round" buttons disabled until all have initiative
o	Actors without initiative positioned at far right of initiative tracker, sorted alphabetically by name within that section
•	Initiative Modifier Precedence: If an encounter is loaded and an actor's initiative modifier from the main actor database mismatches the one stored within the encounter save, the encounter's saved value takes precedence
3.3.4 Turn Order Display
•	Layout: Horizontal arrangement from left (highest initiative) to right (lowest initiative)
•	Actor Tile Sizing: 
o	All actor portraits standardized to 1:1.5 aspect ratio
o	Inactive actors: maximum height = (screen height ÷ 2) × 0.8
o	Active actor: maximum height = screen height ÷ 2 (~20% larger than inactive)
o	Tiles dynamically scaled to span full screen width with margins
o	Consistent margins between tiles and at screen edges
o	Upper edges aligned flush with top of screen
•	Visual Indicators: 
o	Current active actor portrait displayed larger
o	Actors who completed turn greyed out
o	Round counter prominently displayed
o	Status effect icons overlaid on portraits
3.3.5 Turn Management Controls
Always Visible Below Tracker:
•	Left Side: Previous Turn (top), Previous Round (bottom)
•	Right Side: Next Turn (top), Next Round (bottom)
•	Visual Feedback: 
o	"Next Turn" greys out previous actor and enlarges next
o	Skip actors who already acted (remain greyed out)
o	"Next Turn" greyed out when all actors have acted
o	"Previous Turn" greyed out when first actor is active
o	"Next Round" and "Previous Round" always available
Turn Actions:
•	Skip turn (actor remains active but turn passes)
•	End turn (actor marked as completed for current round)
3.3.6 Adding Actors Mid-Encounter
•	Modify Encounter Button: Access actor library to add new participants
•	Display: Actor name, thumbnail, and category
•	Requirement: Manual initiative setting required before addition
•	No Actor Management: Cannot change pictures, names, or types during encounter
3.3.7 Initiative Rolling During Encounter
•	Roll for All: Overwrites all existing initiative values (with confirmation prompt)
•	Roll for NPCs Only: Overwrites only NPC initiative values (with confirmation prompt)
•	Confirmation Required: If initiative already set, prompt before overwriting
3.4 Actor Context Menu System
3.4.1 Context Menu Access
•	Trigger: Tap on actor portrait during encounter
•	Interface: Bottom sheet sliding up from bottom (max 45% screen height, 90% screen width)
•	Visual Feedback: 
o	Selected Actor Highlighting: The selected actor is displayed without any grey or colored overlays (no "completed turn" grey, no "missing initiative" red, and no "context menu open for another actor" green tint). They still retain their size distinction. When the context menu is closed, any previously applicable overlays will reappear if their state still requires it.
o	Other Actors Green Tint: All other actors tinted green while maintaining visual state distinctions. This green layer appears on top of the greyed-out layer. The red overlay for missing initiative is exclusive with the green overlay; if an actor is missing initiative and a different actor's context menu is open, the green overlay takes precedence, hiding the red.
o	Actor name displayed at top of bottom sheet
3.4.2 Context Menu Sections
Initiative Section:
•	Current initiative value display
•	Edit initiative field
•	Move Left/Move Right buttons (side by side): These buttons are only for resolving ties among Player Characters (and for the off-chance that a non-player actor has the same initiative as a player-character). They are not for general manual reordering.
Conditions Section:
•	Display all 15 D&D conditions
•	Each condition with: Toggle (on/off), Count field, Permanent checkbox
•	Validation: If "Permanent" is checked, the Count field is greyed out. The count value can remain visible but is overridden. "Permanent" means the condition will be shown until the encounter ends or it is removed manually. If neither "Permanent" is selected nor a count is provided for an active condition, the app should ask the user to either specify a count or select "Permanent" before being allowed to continue.
•	Duration Counting: The current round counts as one round for the countdown.
Actor Actions:
•	Remove actor from encounter
Encounter Management:
•	Save Encounter: Simple text input dialog with auto-generated name pre-filled. If saving a previously loaded encounter, a new save is generated with a new auto-generated name, rather than overwriting the original.
•	Load Encounter (from detailed list)
•	End Encounter
3.5 Status Effects System
3.5.1 Pre-built Condition Library
15 D&D Conditions (cosmetic only, no functional impact):
1.	Blinded - You can't see
2.	Charmed - You are charmed
3.	Deafened - You can't hear
4.	Exhaustion - You are exhausted
5.	Frightened - You are frightened
6.	Grappled - You are grappled
7.	Incapacitated - You can't take actions or reactions
8.	Invisible - You can't be seen
9.	Paralyzed - You are paralyzed
10.	Petrified - You are transformed into stone
11.	Poisoned - You are poisoned
12.	Prone - You are prone
13.	Restrained - You are restrained
14.	Stunned - You are stunned
15.	Unconscious - You are unconscious
3.5.2 Condition Properties
•	Name: Required (from pre-built list)
•	Icon: Custom XML icons bundled as app resources
•	Duration: Optional, measured in actor turns
•	Permanent: Default option; when unchecked, requires turn count
3.5.3 Condition Management
•	Application: Via actor context menu condition section
•	Duration Tracking: Optional countdown per application
•	Automatic Removal: Conditions automatically removed when duration expires (no notification)
•	Manual Removal: Via context menu toggle
•	Visual Display: Condition icons overlaid on actor portraits (right side, vertically stacked, centered, consistent size)
3.6 Data Persistence and Storage
3.6.1 Encounter Persistence
•	Save System: Save current encounter state (actors, initiative order, turn status, conditions, round number)
•	Continue System: Load previously saved encounter to resume
•	End Encounter: Confirmation prompt to save or discard before ending
•	No Auto-save: Manual save only
3.6.2 Data Storage Architecture
•	Database: Local SQLite database using Room Persistence Library. All structured application data, including actors, encounters, conditions, and their associated data (like icon references), will be stored in this database.
•	File Storage: Custom images in app's internal storage
•	App Resources: Default images and condition icons bundled with app
•	Pre-population: Built-in conditions populated on first app launch
•	Fallback: Default pictures used in case of database corruption
3.6.3 Backup and Reset Options (Settings Screen)
•	Reset All Actors: This will clear all actor data. Encounter saves do not store actor names or pictures directly, relying on references to actors in the main actor database. Deleting an actor invalidates any saved encounters that reference that actor. When an encounter is loaded, the system must check if all actors referenced in that encounter still exist; if any are missing, the encounter will not be loaded. Therefore, selecting "Reset All Actors" will also delete all saved encounters. The user will be informed of this in the confirmation prompt.
•	Reset All Encounters: Preserves actor library
•	Set Background Picture: File picker for custom background
•	Confirmation: Irreversible action confirmation prompts for all reset options
4. User Interface Requirements
4.1 Screen Layouts
4.1.1 Main Screen
•	Orientation: Landscape only
•	Layout: Grid layout with large buttons/cards
•	Background: Customizable background image or black default
4.1.2 Encounter Screen
•	Initiative Tracker: Positioned at top, tiles aligned to upper screen edge
•	Turn Controls: Below tracker (Previous/Next Turn and Round buttons)
•	Context Menu: Bottom sheet (max 45% height, 90% width)
•	Background: Customizable background visible behind tracker
4.1.3 Actor Management Screens
•	Library View: List with search, sort, filter capabilities
•	Actor Display: Name, thumbnail, category consistently shown
•	Standard Forms: For actor creation/editing
4.2 Visual Design Standards
4.2.1 Actor Tiles
•	Aspect Ratio: 1:1.5 portrait orientation
•	Sizing: Dynamic scaling to fill screen width with margins
•	Active State: ~20% larger than inactive actors
•	Completed State: Greyed out appearance
•	Missing Initiative: Red overlay highlight
4.2.2 Status Effects
•	Icon Placement: Right side of portraits, vertically stacked and centered
•	Icon Style: Custom XML icons with consistent sizing
•	Visibility: Clear overlay that doesn't obscure portrait
4.2.3 Context Menu Highlighting
•	Selected Actor: Special highlighting distinct from active state
•	Other Actors: Green tint overlay while preserving state distinctions
•	Background: Maintains visibility of tracker during menu use
4.3 Interaction Patterns
•	Context Menus: Bottom sheet for actor management
•	File Selection: Standard Android photo picker for images
•	Touch Controls: Standard Android touch interactions
•	Drag and Drop: Not implemented in MVP (manual initiative editing instead)
•	Confirmation Dialogs: For destructive actions and overwrites
5. Technical Requirements
5.1 Performance Standards
•	Smooth Interactions: Responsive scrolling and UI updates
•	Initiative Reordering: Immediate visual feedback when values change
•	Image Handling: Efficient loading and caching with minimal memory footprint
•	Database Operations: Fast queries for actor and encounter management
5.2 Platform Compatibility
•	Target Development: Current Android SDK for ease of implementation
•	Backward Compatibility: As feasible without significant complexity increase
•	Device Support: Standard Android phones in landscape orientation
5.3 Data Management
•	Local Storage: All data stored on device
•	No Network: Offline-only application
•	Data Integrity: Graceful handling of corruption with fallback defaults
•	Performance: Optimized queries and image caching
6. User Experience Requirements
6.1 Usability Standards
•	Intuitive Navigation: Clear workflow between screens
•	Visual Feedback: Immediate response to user actions
•	Error Prevention: Validation and confirmation for critical actions
•	Consistent Patterns: Standard Android UI behaviors throughout
6.2 Learning Curve
•	Target Users: Familiar with tabletop RPG concepts
•	Minimal Training: Self-explanatory interface for D&D players
•	Clear States: Visual indicators for all system states
7. Constraints and Assumptions
7.1 Project Constraints
•	Platform: Android only
•	Network: Offline-only operation
•	Usage: Single-device, personal use
•	Development: Prioritize implementation ease over backward compatibility
7.2 User Assumptions
•	Knowledge: Familiar with tabletop RPG concepts and D&D combat
•	Device: Basic Android operation skills
•	Storage: Sufficient device storage for images and data
•	Screen: Landscape orientation preferred/available
8. Future Enhancement Considerations
8.1 Potential Additions (Out of Scope for MVP)
•	Multiple turns per actor per round
•	Automated initiative modifiers and temporary bonuses
•	Additional actor statistics (HP, AC, etc.)
•	Network sharing capabilities
•	Advanced status effect systems with functional impact
•	Campaign management features
•	Drag-and-drop initiative reordering
•	Custom condition creation
•	Enhanced visual effects and animations
8.2 Technical Debt Considerations
•	UI Refinements: More polished condition management interface
•	Performance: Advanced image optimization
•	Accessibility: Enhanced support features
•	Data Export: Backup and sharing capabilities
________________________________________
Document Version: 2.1
Date: July 10, 2025
Author: Complete Requirements Analysis
Status: Ready for Development

