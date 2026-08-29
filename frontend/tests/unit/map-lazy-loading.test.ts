import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const explorerSource = readFileSync(
  new URL('../../src/domain/map/components/google-map-explorer.tsx', import.meta.url),
  'utf8',
);

test('지도 SDK와 클러스터러 실행 코드는 사용자 로드 요청 뒤에 동적으로 내려받는다', () => {
  assert.doesNotMatch(explorerSource, /^import \{[^}]+\} from '@googlemaps\/js-api-loader';/m);
  assert.match(explorerSource, /import type \{[\s\S]+\} from '@googlemaps\/markerclusterer';/);

  const loadGuardIndex = explorerSource.indexOf('if (!shouldLoadMap || !mapContainerRef.current) return;');
  const loaderImportIndex = explorerSource.indexOf("import('@googlemaps/js-api-loader')");
  const clustererImportIndex = explorerSource.indexOf("import('@googlemaps/markerclusterer')");

  assert.ok(loadGuardIndex >= 0);
  assert.ok(loaderImportIndex > loadGuardIndex);
  assert.ok(clustererImportIndex > loadGuardIndex);
});
