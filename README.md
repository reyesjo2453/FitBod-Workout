# FitBod Local

FitBod Local is a browser-based workout planner and log. It builds routines around your training goal, available equipment, workout split, and estimated muscle recovery, then lets you record sets and review your progress. The routine generation runs in the app; no AI account or API key is required.

## What you can do

- **Plan a workout:** Use the weekly Coach Greg schedule or generate a fresh-muscle, push/pull/legs, upper/lower, or full-body routine. Change the schedule and choose which equipment you have.
- **Log your sets:** Record weight and reps, mark sets complete, add sets, and add or swap exercises while training. Choose pounds or kilograms.
- **Time your session and rests:** Follow the workout timer and adjustable rest timer, with an audible chime when rest ends if your browser allows audio.
- **View muscle recovery:** Check an interactive front-and-back muscle map and readiness breakdown. The recovery percentages are estimates based on logged exercise and time since training; you can adjust them manually.
- **Browse exercises:** Search and filter the built-in exercise library by muscle and equipment.
- **Review progress:** See completed workout logs, summary stats, and personal records for exercises.
- **Keep a backup:** Export your data as JSON and import it into another browser or device.

## Get started

1. Open `index.html` in a current browser, preferably from a web host with an HTTPS address for a smoother install experience. The app is a single HTML file and needs no account.
2. In **Gym Profile & Equipment**, choose your training goal (hypertrophy, strength, or endurance), preferred split, weight unit, equipment, and weekly schedule.
3. Open **Workout** to generate or populate a routine. Start the session, enter your actual weights and reps, and mark sets complete as you go.
4. Finish the session to save it to **History**. Visit **Recovery** to review the estimated readiness of each muscle group.

You can use **Install App** in the settings on supported browsers, or add the page to your home screen. Install support varies by browser. Keep the original web address available because this version does not register an offline service worker.

## Your data

Workout history, personal records, settings, and recovery estimates are saved in this browser's local storage. They do not sync automatically between browsers or devices. In **Gym Profile & Equipment → Data & Local Storage**, select **Export JSON Backup** regularly, and use **Import JSON Backup** to restore it elsewhere. Clearing site data or using **Reset All Progress** removes the locally saved information.

The muscle recovery display is an app-generated estimate, not a medical assessment. Adjust your training to how you actually feel and any advice from your clinician or coach.

## Technical notes

The app lives in `index.html` and uses JavaScript in the browser with no server-side account needed for local workout logging. The optional Fitness Connect connection stores a private snapshot when you choose to sync. Its interface loads Tailwind CSS and icons from CDNs, so those resources require an internet connection on first load.

## ChatGPT connection (optional)

Use **Sync with ChatGPT** in the app's settings to send a snapshot to [Fitness Connect](https://fitness-connect.reyesjo2453.chatgpt.site). Sign in, review the incoming record counts, and select **Save synced records**. If the new window cannot receive the records, export a JSON backup from this app and upload it at Fitness Connect instead.

Install and connect the personal **Fitness Connect** plugin in ChatGPT to read your synced records. The connection supports nutrition summaries, meals, recipes, weight history, completed workouts, personal records, and recovery estimates. It cannot modify your app records. Sync again after changes; ChatGPT reads the last synced copy. Only the owner's account can access this personal connection, and Gemini API keys are excluded from stored snapshots.
