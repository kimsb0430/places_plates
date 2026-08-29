import assert from 'node:assert/strict';
import test from 'node:test';
import { deterPublicImageTransfer } from '../../src/domain/post/public-image-protection';

test('공개 이미지의 우클릭·드래그·복사 이벤트는 기본 동작을 막는다', () => {
  let preventedCount = 0;
  const event = {
    preventDefault() {
      preventedCount += 1;
    },
  };

  deterPublicImageTransfer(event);
  deterPublicImageTransfer(event);
  deterPublicImageTransfer(event);

  assert.equal(preventedCount, 3);
});
