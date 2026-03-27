const fs = require('fs');
const lines = fs.readFileSync('compile.txt', 'utf8').split('\n');
for (let i = 0; i < lines.length; i++) {
    const l = lines[i].trim();
    if (l.includes('.java:') && l.includes('Fehler:')) {
        console.log(l);
    } else if (l.includes('Fehler:') && !l.includes('Fehler: Symbol') && !l.includes('Fehler: Inkompatible') && !l.includes('Fehler: Package')) {
        console.log(l);
    }
}
