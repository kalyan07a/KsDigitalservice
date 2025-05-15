// Disable right-click context menu and common inspect element shortcuts
document.addEventListener('contextmenu', function(e) {
    e.preventDefault();
});

document.addEventListener('keydown', function(e) {
    // Disable Ctrl + Shift + I (Inspect Element)
    if (e.ctrlKey && e.shiftKey && e.key === 'I') {
        e.preventDefault();
    }
    // Disable F12 key (DevTools)
    if (e.key === 'F12') {
        e.preventDefault();
    }
    // Disable Ctrl + U (View Source)
    if (e.ctrlKey && e.key === 'U') {
        e.preventDefault();
    }
});
