import { IonicChromecast } from 'ionic-chromecast';

// Simple UI logger
function appendLog(msg) {
  const el = document.getElementById('eventLog');
  if (!el) return;
  const li = document.createElement('li');
  const ts = new Date().toLocaleTimeString();
  li.textContent = `[${ts}] ${msg}`;
  el.prepend(li);
}

// Initialize Cast SDK when app loads
window.addEventListener('DOMContentLoaded', async () => {
    console.log('Initializing Chromecast...');
    appendLog('App loaded');
    
    const statusEl = document.getElementById('castStatus');
    
    try {
        // Using Google's Default Media Receiver (CC1AD845)
        // This ONLY works with whitelisted Google sample videos
        const result = await IonicChromecast.initialize({
            receiverApplicationId: 'CC1AD845' // Google Default Media Receiver
        });
        
        if (result.success) {
            statusEl.textContent = '✅ Cast SDK initialized successfully';
            statusEl.style.color = 'green';
            console.log('Cast SDK initialized:', result);
            appendLog('Cast SDK initialized');
        } else {
            statusEl.textContent = '❌ Cast SDK initialization failed';
            statusEl.style.color = 'red';
            appendLog('Cast SDK initialization failed');
        }
    } catch (error) {
        console.error('Failed to initialize Cast SDK:', error);
        statusEl.textContent = '❌ Error: ' + (error?.message || error);
        statusEl.style.color = 'red';
        appendLog('Initialize error: ' + (error?.message || error));
    }

    // Hook button that had inline onclick without parentheses
    const btnCheck = document.getElementById('btnCheckDevices');
    if (btnCheck) btnCheck.onclick = () => window.checkDevices();

    // Attach listeners from plugin to surface session/media events
    try {
      await IonicChromecast.addListener('sessionStarted', (e) => {
        appendLog('event: sessionStarted ' + JSON.stringify(e || {}));
        const sessionEl = document.getElementById('sessionStatus');
        if (sessionEl) { sessionEl.textContent = '✅ Connected'; sessionEl.style.color = 'green'; }
      });
      await IonicChromecast.addListener('sessionEnded', (e) => {
        appendLog('event: sessionEnded ' + JSON.stringify(e || {}));
        const sessionEl = document.getElementById('sessionStatus');
        if (sessionEl) { sessionEl.textContent = '—'; sessionEl.style.color = '#333'; }
      });
      await IonicChromecast.addListener('mediaLoaded', (e) => {
        appendLog('event: mediaLoaded ' + JSON.stringify(e || {}));
        const playEl = document.getElementById('playStatus');
        if (playEl) { playEl.textContent = '▶️ Playing on TV'; playEl.style.color = 'green'; }
      });
      await IonicChromecast.addListener('mediaError', (e) => {
        appendLog('event: mediaError ' + JSON.stringify(e || {}));
      });
      await IonicChromecast.addListener('playbackStatusChanged', (e) => {
        appendLog('event: playbackStatusChanged ' + JSON.stringify(e || {}));
      });
    } catch (_) {
      // ignore listener setup errors
    }
});

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    IonicChromecast.echo({ value: inputValue })
}

// Helper: wait until there is an active Cast session, with timeout
async function waitForSessionActive(timeoutMs = 30000, intervalMs = 750) {
    const start = Date.now();
    while (Date.now() - start < timeoutMs) {
        try {
            const { active } = await IonicChromecast.isSessionActive();
            if (active) return true;
        } catch (_) {}
        await new Promise(r => setTimeout(r, intervalMs));
    }
    return false;
}

// Check Devices
window.checkDevices = async () => {
    const devicesStatusEl = document.getElementById('devicesStatus');
    devicesStatusEl.textContent = '⏳ Checking for devices...';
    devicesStatusEl.style.color = '#333';

    try {
        const { available, message } = await IonicChromecast.areDevicesAvailable();
        if (available) {
            devicesStatusEl.textContent = '📡 Devices available';
            devicesStatusEl.style.color = 'green';
        } else {
            devicesStatusEl.textContent = '❌ No devices available';
            devicesStatusEl.style.color = 'red';
        }
        if (message) console.log(message);
        appendLog('areDevicesAvailable => ' + available);
    } catch (e) {
        devicesStatusEl.textContent = '⚠️ Error checking devices';
        devicesStatusEl.style.color = 'orange';
        console.error('areDevicesAvailable error', e);
        appendLog('areDevicesAvailable error: ' + (e?.message || e));
    }
}

// Start Casting (only shows the Cast chooser dialog to validate device list)
window.startCasting = async () => {
    const sessionEl = document.getElementById('sessionStatus');
    sessionEl.textContent = '⏳ Opening Cast chooser...';
    sessionEl.style.color = '#333';
    try {
        const result = await IonicChromecast.requestSession();
        appendLog('requestSession (test only) => ' + JSON.stringify(result || {}));
        if (result.success) {
            sessionEl.textContent = '✅ Cast chooser displayed (you can close it)';
            sessionEl.style.color = 'green';
        } else {
            sessionEl.textContent = '❌ Failed to show chooser';
            sessionEl.style.color = 'red';
        }
    } catch (e) {
        sessionEl.textContent = '⚠️ Error showing chooser';
        sessionEl.style.color = 'orange';
        console.error('requestSession error', e);
        appendLog('requestSession error: ' + (e?.message || e));
    }
}

// Play sample video - COMPLETE FLOW: check session, connect if needed, load media
window.playVideo = async () => {
    const playEl = document.getElementById('playStatus');
    const sessionEl = document.getElementById('sessionStatus');
    playEl.textContent = '⏳ Starting cast flow...';
    playEl.style.color = '#333';

    try {
        // STEP 1: Check if there's already an active session
        let { active } = await IonicChromecast.isSessionActive();
        appendLog('STEP 1: isSessionActive => ' + active);

        if (!active) {
            // STEP 2: No session → open Cast chooser dialog
            sessionEl.textContent = '⏳ Opening Cast device selector...';
            sessionEl.style.color = '#333';
            playEl.textContent = '⏳ Waiting for device selection...';
            
            try {
                const reqResult = await IonicChromecast.requestSession();
                appendLog('STEP 2: requestSession => ' + JSON.stringify(reqResult || {}));
                
                if (!reqResult.success) {
                    playEl.textContent = '❌ Failed to open chooser';
                    playEl.style.color = 'red';
                    return;
                }
            } catch (e) {
                appendLog('STEP 2 ERROR: ' + (e?.message || e));
                playEl.textContent = '❌ Error opening chooser';
                playEl.style.color = 'red';
                return;
            }

            sessionEl.textContent = '📲 Select your Chromecast device...';
            sessionEl.style.color = '#333';

            // STEP 3: Wait for user to select device and session to become active
            appendLog('STEP 3: Waiting up to 45s for session connection...');
            const connected = await waitForSessionActive(45000, 800);
            appendLog('STEP 3: waitForSessionActive => ' + connected);

            if (!connected) {
                playEl.textContent = '❌ Device not selected or connection failed';
                playEl.style.color = 'red';
                sessionEl.textContent = '❌ No connection';
                sessionEl.style.color = 'red';
                return;
            }

            sessionEl.textContent = '✅ Connected to Chromecast';
            sessionEl.style.color = 'green';
            appendLog('STEP 3: Session connected successfully');
            
            // STEP 3.5: Wait a moment for the session to fully stabilize
            appendLog('STEP 3.5: Waiting 2s for session to stabilize...');
            await new Promise(r => setTimeout(r, 2000));
        } else {
            appendLog('STEP 1: Session already active, skipping connection');
            sessionEl.textContent = '✅ Already connected';
            sessionEl.style.color = 'green';
        }

        // STEP 4: Load media to the connected Cast device
        playEl.textContent = '⏳ Loading media to TV...';
        appendLog('STEP 4: Sending loadMedia request...');
        
        // Using HTTP instead of HTTPS - some receivers require this
        const result = await IonicChromecast.loadMedia({
            url: 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4', // Whitelisted Google sample video
            metadata: {
                title: 'Sintel',
                subtitle: 'Blender Foundation',
                images: ['http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg'],
                contentType: 'video/mp4',
                duration: 888
            }
        });
        
        appendLog('STEP 4: loadMedia result => ' + JSON.stringify(result || {}));
        
        if (result.success) {
            playEl.textContent = '▶️ Playing on TV!';
            playEl.style.color = 'green';
            appendLog('✅ SUCCESS: Video is now playing on Chromecast');
        } else {
            playEl.textContent = '❌ Failed to load media';
            playEl.style.color = 'red';
            appendLog('❌ FAILED: Media load returned false');
        }
        
    } catch (e) {
        playEl.textContent = '⚠️ Error in cast flow';
        playEl.style.color = 'orange';
        console.error('playVideo error', e);
        appendLog('❌ EXCEPTION: ' + (e?.message || e));
    }
}
