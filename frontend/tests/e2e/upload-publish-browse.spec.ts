import { expect, test, type Page } from '@playwright/test';

const apiOrigin = 'http://127.0.0.1:3211';

test.beforeEach(async ({ request }) => {
  const response = await request.post(`${apiOrigin}/__e2e/reset`);
  expect(response.ok()).toBeTruthy();
});

test('사진 업로드부터 공개 목록과 지도 탐색까지 완료한다', async ({ page }) => {
  await page.goto('/manage');
  await expect(page.getByRole('heading', { name: '기록 관리' })).toBeVisible();

  await page.getByRole('button', { name: /맛집/ }).click();
  await page.locator('input[type="file"]').setInputFiles({
    name: 'c38-e2e.jpg',
    mimeType: 'image/jpeg',
    buffer: Buffer.from([0xff, 0xd8, 0xff, 0xd9]),
  });

  await expect(page).toHaveURL(/\/manage\/drafts\/[0-9a-f-]{36}$/i);
  await expect(page.getByRole('heading', { name: '기록 정보 편집' })).toBeVisible();

  await page.getByLabel(/제목/).fill('C38 교토 점심 기록');
  await page.getByLabel(/방문 월/).fill('2026-08');
  await page.getByLabel(/한줄평/).fill('업로드부터 지도까지 이어지는 회귀 기록');

  await page.getByRole('button', { name: '직접 입력' }).click();
  const manualPlaceForm = page.locator('.manual-place-form');
  await manualPlaceForm.locator('input').nth(0).fill('교토 테스트 식당');
  await manualPlaceForm.locator('input').nth(1).fill('교토시 테스트 거리');
  await manualPlaceForm.locator('input').nth(2).fill('35.011636');
  await manualPlaceForm.locator('input').nth(3).fill('135.768029');
  await manualPlaceForm.getByRole('button', { name: '직접 입력 장소 저장' }).click();

  await expect(page.locator('.connected-place').getByText('교토 테스트 식당')).toBeVisible();
  const publishButton = page.getByRole('button', { name: '이 범위로 게시하기' });
  await expect(publishButton).toBeEnabled({ timeout: 15_000 });
  await page.getByRole('radio', { name: /전체 공개/ }).check();
  await publishButton.click();
  await expect(page.getByRole('heading', { name: '기록 게시 완료' })).toBeVisible();

  await page.goto('/');
  const primaryNavigation = page.getByRole('navigation', { name: '주요 메뉴' });
  await expect(primaryNavigation.getByRole('link')).toHaveCount(2);
  await expect(primaryNavigation.getByRole('link', { name: '기록' })).toBeVisible();
  await expect(primaryNavigation.getByRole('link', { name: '지도' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '나의 기록' })).toBeVisible();
  const writingLink = page.getByRole('link', { name: '기록하기', exact: true });
  await expect(writingLink).toBeVisible();
  const writingLinkBox = await writingLink.boundingBox();
  expect(writingLinkBox?.height).toBeGreaterThanOrEqual(320);
  await expect(page.locator('.hero-actions')).toHaveCount(0);
  await expect(page.locator('.hero-live-photo')).toHaveCount(0);
  await expectNoHorizontalOverflow(page);

  await page.goto('/posts');
  await expect(page.getByRole('heading', { name: '공개 기록' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'C38 교토 점심 기록' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: '공개 기록 카테고리' })
    .getByRole('link', { name: '맛집 공개 기록 1개' })).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.getByRole('link', { name: 'C38 교토 점심 기록 기록 읽기' }).click();
  await expect(page.getByRole('heading', { name: 'C38 교토 점심 기록' })).toBeVisible();
  await expect(page.getByRole('button', { name: '이 기록 삭제' })).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.goto('/map');
  await expect(page.getByRole('heading', { name: '기록 지도' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Google 지도 불러오기' })).toBeVisible();
  await expect(page.getByRole('button', { name: /C38 교토 점심 기록.*교토 테스트 식당/ })).toBeVisible();
  await expect(page.getByText('1개', { exact: true })).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.goto(`/posts/11111111-1111-4111-8111-111111111111`);
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: '이 기록 삭제' }).click();
  await expect(page).toHaveURL('/manage');
  await expect(page.getByText('아직 게시 완료된 기록이 없습니다.')).toBeVisible();
});

async function expectNoHorizontalOverflow(page: Page): Promise<void> {
  const hasOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth + 1,
  );
  expect(hasOverflow).toBeFalsy();
}
