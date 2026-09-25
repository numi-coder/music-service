document.addEventListener('DOMContentLoaded', () => {
    const MAX_SELECT = 2;

    document.querySelectorAll('.feel-card').forEach(card => {
        card.addEventListener('click', (e) => {
            const input = card.querySelector('input');

            // если карточка уже выбрана — снимаем выбор
            if (input.checked) {
                input.checked = false;
                card.classList.remove('active');
                return;
            }

            // считаем выбранные
            const checkedCount = document.querySelectorAll(
                '.feel-card input:checked'
            ).length;

            // не даем выбрать больше 2
            if (checkedCount >= MAX_SELECT) {
                e.preventDefault();
                return;
            }

            input.checked = true;
            card.classList.add('active');
        });
    });
});