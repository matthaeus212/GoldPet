function profile() {
    document.addEventListener("DOMContentLoaded", function () {
        const profileSliders = document.querySelectorAll(".profile_img_wrap");

        profileSliders.forEach((wrapEl, index) => {
            const prevEl = wrapEl.querySelector(".swiper-profile-prev");
            const nextEl = wrapEl.querySelector(".swiper-profile-next");

            // 각 카드별로 유니크한 클래스 부여 (navigation용)
            const prevClass = `swiper-profile-prev-${index}`;
            const nextClass = `swiper-profile-next-${index}`;

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