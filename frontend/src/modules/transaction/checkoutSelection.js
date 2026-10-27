const PREFIX = 'shopflash.checkout.selection.v1.';

function key(userId) {
  return `${PREFIX}${userId}`;
}

export function readCheckoutSelection(userId) {
  if (!Number.isSafeInteger(userId) || userId <= 0) return [];
  try {
    const value = JSON.parse(sessionStorage.getItem(key(userId)) ?? '[]');
    return Array.isArray(value) ? [...new Set(value.filter((id) => Number.isSafeInteger(id) && id > 0))] : [];
  } catch { return []; }
}

export function saveCheckoutSelection(userId, productIds) {
  if (!Number.isSafeInteger(userId) || userId <= 0) return;
  sessionStorage.setItem(key(userId), JSON.stringify(productIds));
}

export function removeCheckoutSelection(userId, purchasedIds) {
  const purchased = new Set(purchasedIds);
  saveCheckoutSelection(userId, readCheckoutSelection(userId).filter((id) => !purchased.has(id)));
}
