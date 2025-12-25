import { BmQr } from 'bm-qr-scanner';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    BmQr.echo({ value: inputValue })
}
