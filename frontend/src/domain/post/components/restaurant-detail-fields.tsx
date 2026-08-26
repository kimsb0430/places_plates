import type { RestaurantPriceRange, RevisitIntention } from '../types';

export interface RestaurantEditorValues {
  rating: string;
  recommendedMenu: string;
  priceRange: RestaurantPriceRange | '';
  waitingMinutes: string;
  revisitIntention: RevisitIntention | '';
}

interface RestaurantDetailFieldsProps {
  value: RestaurantEditorValues;
  onChange: (field: keyof RestaurantEditorValues, value: string) => void;
}

const RATING_OPTIONS = ['5.0', '4.5', '4.0', '3.5', '3.0', '2.5', '2.0', '1.5', '1.0', '0.5'];

export function RestaurantDetailFields({ value, onChange }: RestaurantDetailFieldsProps) {
  return (
    <section className="restaurant-detail-section" aria-labelledby="restaurant-detail-title">
      <div className="restaurant-detail-heading">
        <div>
          <p className="overline">RESTAURANT NOTE</p>
          <h2 id="restaurant-detail-title">맛집에서 기억할 것</h2>
        </div>
        <span>모두 선택</span>
      </div>

      <div className="restaurant-detail-fields">
        <label htmlFor="draft-rating">
          <span>평점</span>
          <select
            id="draft-rating"
            name="rating"
            value={value.rating}
            onChange={(event) => onChange('rating', event.target.value)}
          >
            <option value="">평점을 남기지 않음</option>
            {RATING_OPTIONS.map((rating) => (
              <option key={rating} value={rating}>{rating} / 5.0</option>
            ))}
          </select>
        </label>

        <label htmlFor="draft-price-range">
          <span>가격대</span>
          <select
            id="draft-price-range"
            name="priceRange"
            value={value.priceRange}
            onChange={(event) => onChange('priceRange', event.target.value)}
          >
            <option value="">가격대를 선택하지 않음</option>
            <option value="BUDGET">₩ 가볍게</option>
            <option value="MODERATE">₩₩ 보통</option>
            <option value="EXPENSIVE">₩₩₩ 높은 편</option>
            <option value="LUXURY">₩₩₩₩ 특별한 날</option>
          </select>
        </label>

        <label htmlFor="draft-recommended-menu" className="is-wide">
          <span>추천 메뉴</span>
          <input
            id="draft-recommended-menu"
            name="recommendedMenu"
            value={value.recommendedMenu}
            maxLength={300}
            onChange={(event) => onChange('recommendedMenu', event.target.value)}
            placeholder="다시 주문하고 싶은 메뉴를 적어보세요"
          />
          <small>{value.recommendedMenu.length}/300</small>
        </label>

        <label htmlFor="draft-waiting-minutes">
          <span>대기시간</span>
          <div className="restaurant-number-field">
            <input
              id="draft-waiting-minutes"
              name="waitingMinutes"
              type="number"
              value={value.waitingMinutes}
              min={0}
              step={1}
              inputMode="numeric"
              onChange={(event) => {
                const waitingMinutes = event.target.value;
                if (waitingMinutes === '' || /^\d+$/.test(waitingMinutes)) {
                  onChange('waitingMinutes', waitingMinutes);
                }
              }}
              placeholder="0"
            />
            <span>분</span>
          </div>
        </label>

        <label htmlFor="draft-revisit-intention">
          <span>재방문 의사</span>
          <select
            id="draft-revisit-intention"
            name="revisitIntention"
            value={value.revisitIntention}
            onChange={(event) => onChange('revisitIntention', event.target.value)}
          >
            <option value="">선택하지 않음</option>
            <option value="YES">다시 가고 싶어요</option>
            <option value="MAYBE">기회가 되면 가고 싶어요</option>
            <option value="NO">재방문은 어려워요</option>
          </select>
        </label>
      </div>
    </section>
  );
}
