import { IonicChromecast } from 'ionic-chromecast';

// Initialize Cast SDK when app loads
window.addEventListener('DOMContentLoaded', async () => {
    console.log('Initializing Chromecast...');
    
    const statusEl = document.getElementById('castStatus');
    
    try {
        const result = await IonicChromecast.initialize({
            receiverApplicationId: 'CC1AD845' // Default Media Receiver
        });
        
        if (result.success) {
            statusEl.textContent = '✅ Cast SDK initialized successfully';
            statusEl.style.color = 'green';
            console.log('Cast SDK initialized:', result);
        } else {
            statusEl.textContent = '❌ Cast SDK initialization failed';
            statusEl.style.color = 'red';
        }
    } catch (error) {
        console.error('Failed to initialize Cast SDK:', error);
        statusEl.textContent = '❌ Error: ' + error.message;
        statusEl.style.color = 'red';
    }
});

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    IonicChromecast.echo({ value: inputValue })
}
