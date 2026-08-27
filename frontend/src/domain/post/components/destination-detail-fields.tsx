export interface DestinationEditorValues {
  recommendedTime: string;
  durationMinutes: string;
  highlights: string;
  travelTips: string;
}

interface DestinationDetailFieldsProps {
  value: DestinationEditorValues;
  onChange: (field: keyof DestinationEditorValues, value: string) => void;
}

export function DestinationDetailFields({ value, onChange }: DestinationDetailFieldsProps) {
  return (
    <section className="destination-detail-section" aria-labelledby="destination-detail-title">
      <div className="destination-detail-heading">
        <div>
          <p className="overline">DESTINATION NOTE</p>
          <h2 id="destination-detail-title">여행지에서 기억할 것</h2>
        </div>
        <span>모두 선택</span>
      </div>

      <div className="destination-detail-fields">
        <label htmlFor="draft-recommended-time">
          <span>추천 방문 시간</span>
          <input
            id="draft-recommended-time"
            name="recommendedTime"
            value={value.recommendedTime}
            maxLength={100}
            onChange={(event) => onChange('recommendedTime', event.target.value)}
            placeholder="예: 해 뜨기 전 이른 아침"
          />
          <small>{value.recommendedTime.length}/100</small>
        </label>

        <label htmlFor="draft-duration-minutes">
          <span>소요시간</span>
          <div className="destination-number-field">
            <input
              id="draft-duration-minutes"
              name="durationMinutes"
              type="number"
              value={value.durationMinutes}
              min={0}
              step={1}
              inputMode="numeric"
              onChange={(event) => {
                const durationMinutes = event.target.value;
                if (durationMinutes === '' || /^\d+$/.test(durationMinutes)) {
                  onChange('durationMinutes', durationMinutes);
                }
              }}
              placeholder="0"
            />
            <span>분</span>
          </div>
        </label>

        <label htmlFor="draft-highlights" className="is-wide">
          <span>볼거리</span>
          <textarea
            id="draft-highlights"
            name="highlights"
            value={value.highlights}
            maxLength={5000}
            rows={4}
            onChange={(event) => onChange('highlights', event.target.value)}
            placeholder="놓치고 싶지 않은 장면이나 체험을 적어보세요"
          />
          <small>{value.highlights.length.toLocaleString('ko-KR')}/5,000</small>
        </label>

        <label htmlFor="draft-travel-tips" className="is-wide">
          <span>여행 팁</span>
          <textarea
            id="draft-travel-tips"
            name="travelTips"
            value={value.travelTips}
            maxLength={5000}
            rows={4}
            onChange={(event) => onChange('travelTips', event.target.value)}
            placeholder="교통, 준비물, 혼잡 시간처럼 다음 방문에 도움 될 내용을 남겨보세요"
          />
          <small>{value.travelTips.length.toLocaleString('ko-KR')}/5,000</small>
        </label>
      </div>
    </section>
  );
}
