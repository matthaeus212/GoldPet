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

function friendFind() {
    document.addEventListener("DOMContentLoaded", function () {
        const petSliders = document.querySelectorAll(".pet_img_wrap");

        petSliders.forEach((wrapEl, index) => {
            const prevEl = wrapEl.querySelector(".swiper-pet-prev");
            const nextEl = wrapEl.querySelector(".swiper-pet-next");

            // 각 카드별로 유니크한 클래스 부여 (navigation용)
            const prevClass = `swiper-pet-prev-${index}`;
            const nextClass = `swiper-pet-next-${index}`;

            prevEl.classList.add(prevClass);
            nextEl.classList.add(nextClass);

            // Swiper 초기화
            const swiper = new Swiper(wrapEl, {
                // 기본 옵션 (원하는대로 수정 가능)
                spaceBetween:"5%",

                // wrapper / slide 클래스 HTML에 맞게 지정
                wrapperClass: "swiper-wrapper",
                slideClass: "swiper-slide",

                // 내비게이션 버튼
                navigation: {
                    prevEl: `.${prevClass}`,
                    nextEl: `.${nextClass}`,
                },
            });
        });
    });
}
function layout(){

}



































function main(){
    const mainSwiper = new Swiper('.main_slide', {
        loop: false,
        parallax: true,
        spaceBetween:"5%",
    });
    document.addEventListener("DOMContentLoaded", function () {
        const insideSliders = document.querySelectorAll(".main_slide_inside");

        insideSliders.forEach((containerEl) => {
            const prevEl = containerEl.querySelector(".swiper-inside-prev");
            const nextEl = containerEl.querySelector(".swiper-inside-next");

            new Swiper(containerEl, {
                loop: false,
                spaceBetween: 0,
                allowTouchMove: false,
                wrapperClass: "swiper-wrapper",
                slideClass: "swiper-slide",
                navigation: {
                    prevEl: prevEl, // HTMLElement 직접 전달
                    nextEl: nextEl,
                },
            });
        });
    });

    const $introTit = document.querySelectorAll("#intro .tit_wrap li");
    const introSwiper = new Swiper('.intro_slide', {
        loop: false,
        spaceBetween:"5%",
        pagination: {
            el: ".swiper-pagination",
        },
        on : {
            slideChange:function(){
                $introTit.forEach((tit) => tit.classList.remove("active"));

                if ($introTit[this.activeIndex]) {
                    $introTit[this.activeIndex].classList.add("active");
                }
            }
        }
    });

}



























