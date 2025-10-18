import { IonicChromecast } from 'ionic-chromecast';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    IonicChromecast.echo({ value: inputValue })
}
