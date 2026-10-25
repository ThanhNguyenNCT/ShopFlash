import assert from 'node:assert/strict';
import test from 'node:test';
import { isSameCustomer } from './identity.js';

test('danh tính backend phải khớp khách đang chọn', () => {
  const customer = { email: 'customer@shopflash.local', role: 'CUSTOMER' };
  assert.equal(isSameCustomer(customer, { email: customer.email, role: 'CUSTOMER' }), true);
  assert.equal(isSameCustomer(customer, { email: 'another@shopflash.local', role: 'CUSTOMER' }), false);
  assert.equal(isSameCustomer(customer, { email: customer.email, role: 'ADMIN' }), false);
  assert.equal(isSameCustomer({ email: customer.email, role: 'ADMIN' }, { email: customer.email, role: 'CUSTOMER' }), false);
  assert.equal(isSameCustomer(null, { email: customer.email, role: 'CUSTOMER' }), false);
});
