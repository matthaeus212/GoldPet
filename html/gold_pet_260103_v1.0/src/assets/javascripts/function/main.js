
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



























