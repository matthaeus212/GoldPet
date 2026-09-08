"use strict";

let winH = window.innerHeight,
    winW = window.innerWidth;
let winSc;
const $body = document.getElementsByTagName("body"),
    $html = document.getElementsByTagName("html");
window.addEventListener("scroll", (event) => {

});
window.addEventListener("resize", (event) => {
    winW = window.innerWidth;
});


layout();
main();
friendFind();
profile();