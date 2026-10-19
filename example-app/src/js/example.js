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

// IDs disponibles para prueba (usar siempre el Default Media Receiver a menos que se sobreescriba)
const DEFAULT_MEDIA_RECEIVER_ID = 'CC1AD845';      // Default Media Receiver (compatible y sin UI extra)
const CAST_VIDEOS_SAMPLE_ID = '4F8B3483';          // Receiver demo CAF (solo para pruebas explícitas)

// Permite sobreescribir el receiver por query param ?receiverId=XXXX; si no, usa el default seguro
const queryReceiverId = new URLSearchParams(window.location.search).get('receiverId');
const DEFAULT_RECEIVER_ID = queryReceiverId || DEFAULT_MEDIA_RECEIVER_ID;

// Initialize Cast SDK when app loads
window.addEventListener('DOMContentLoaded', async () => {
    console.log('Initializing Chromecast...');
    appendLog('App loaded');
    appendLog(`Receiver ID in use: ${DEFAULT_RECEIVER_ID}${queryReceiverId ? ' (from URL)' : ' (default)'}`);
    
    const statusEl = document.getElementById('castStatus');
    const receiverEl = document.getElementById('receiverIdInfo');
    if (receiverEl) {
        receiverEl.textContent = `Receiver ID: ${DEFAULT_RECEIVER_ID}${queryReceiverId ? ' (URL override)' : ' (default)'}`;
    }
    
    try {
        const result = await IonicChromecast.initialize({
            receiverApplicationId: DEFAULT_RECEIVER_ID
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

    // Hook button (redundante con onclick del HTML, pero mantiene compatibilidad)
    const btnCheck = document.getElementById('btnCheckDevices');
    if (btnCheck) btnCheck.onclick = () => window.checkDevices();

    // Attach listeners from plugin to surface session/media events
    try {
      await IonicChromecast.addListener('sessionStarted', (e) => {
        appendLog('event: sessionStarted ' + JSON.stringify(e || {}));
        const sessionEl = document.getElementById('sessionStatus');
        if (sessionEl) {
          sessionEl.textContent = `✅ Connected to ${e?.deviceName || (e?.transport === 'dlna' ? 'DLNA TV' : 'Chromecast')}`;
          sessionEl.style.color = 'green';
        }
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
            devicesStatusEl.textContent = '❌ No Cast/DLNA devices found' + (message ? ` — ${message}` : '');
            devicesStatusEl.style.color = 'red';
        }
        if (message) console.log(message);
        appendLog('areDevicesAvailable => ' + available + (message ? `; ${message}` : ''));
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
        if (result?.dlnaDiscovery) appendLog('DLNA discovery => ' + result.dlnaDiscovery);
        if (result.success) {
            sessionEl.textContent = result.transport === 'dlna'
                ? `✅ Connected to ${result.deviceName || 'DLNA TV'}`
                : `✅ Cast chooser displayed${result.dlnaDiscovery ? `; ${result.dlnaDiscovery}` : ''}`;
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
window.playVideo = async (format = 'mp4') => {
    const playEl = document.getElementById('playStatus');
    const sessionEl = document.getElementById('sessionStatus');
    playEl.textContent = '⏳ Starting cast flow...';
    playEl.style.color = '#333';

    try {
        // STEP 1: Check if there's already an active session
        const initialSession = await IonicChromecast.isSessionActive();
        let { active } = initialSession;
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

            sessionEl.textContent = '📲 Select your TV...';
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

            const selectedSession = await IonicChromecast.isSessionActive();
            sessionEl.textContent = `✅ Connected to ${selectedSession.deviceName || (selectedSession.transport === 'dlna' ? 'DLNA TV' : 'Chromecast')}`;
            sessionEl.style.color = 'green';
            appendLog('STEP 3: Session connected successfully');
            
            // STEP 3.5: Wait a moment for the session to fully stabilize
            appendLog('STEP 3.5: Waiting 2s for session to stabilize...');
            await new Promise(r => setTimeout(r, 2000));
        } else {
            appendLog('STEP 1: Session already active, skipping connection');
            sessionEl.textContent = `✅ Connected to ${initialSession.deviceName || (initialSession.transport === 'dlna' ? 'DLNA TV' : 'Chromecast')}`;
            sessionEl.style.color = 'green';
        }

        // STEP 4: Load media to the connected Cast device
        playEl.textContent = '⏳ Loading media to TV...';
        appendLog('STEP 4: Sending loadMedia request...');
        
        const isHls = format === 'hls';
        const isLongMp4 = format === 'long-mp4';
        const videoUrl = isHls
            ? 'https://r2.animeslime.online/N/Naruto/LAT/Naruto%20Latino%20-%20001/index.m3u8'
            : isLongMp4
                ? 'https://archive.org/download/youtube-aqz-KE-bpKQ/aqz-KE-bpKQ.mp4'
                : 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4';
        const contentType = isHls ? 'application/x-mpegurl' : 'video/mp4';
        appendLog(`STEP 4: loadMedia url=${videoUrl} ct=${contentType} receiver=${DEFAULT_RECEIVER_ID}`);

        const result = await IonicChromecast.loadMedia({
            url: videoUrl,
            metadata: {
                title: isHls ? 'Naruto Latino - 001' : isLongMp4 ? 'W3C WAI-Tools Overview' : 'Flower',
                subtitle: isHls ? 'HLS test' : isLongMp4 ? 'Long MP4 seek test' : 'MDN sample video',
                contentType,
                ...(isHls ? {} : { duration: isLongMp4 ? 581.248 : 5 })
            }
        });
        
        appendLog('STEP 4: loadMedia result => ' + JSON.stringify(result || {}));
        
        if (result.success) {
            playEl.textContent = '▶️ Playing on TV!';
            playEl.style.color = 'green';
            appendLog('✅ SUCCESS: Video was sent to the TV');
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

// Stop active Cast session
window.stopCasting = async () => {
    const sessionEl = document.getElementById('sessionStatus');
    const playEl = document.getElementById('playStatus');
    sessionEl.textContent = '⏳ Ending session...';
    sessionEl.style.color = '#333';

    try {
        const result = await IonicChromecast.endSession();
        appendLog('stopCasting => ' + JSON.stringify(result || {}));
        if (result.success) {
            sessionEl.textContent = '⏹ Session ended';
            sessionEl.style.color = '#333';
            if (playEl) { playEl.textContent = '—'; playEl.style.color = '#333'; }
        } else {
            sessionEl.textContent = '❌ Could not end session';
            sessionEl.style.color = 'red';
        }
    } catch (e) {
        appendLog('stopCasting error: ' + (e?.message || e));
        sessionEl.textContent = '⚠️ Error ending session';
        sessionEl.style.color = 'orange';
    }
}
