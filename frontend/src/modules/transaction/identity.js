export function isSameCustomer(displayUser, serverIdentity) {
  return displayUser?.role === 'CUSTOMER'
    && serverIdentity?.role === 'CUSTOMER'
    && typeof displayUser.email === 'string'
    && serverIdentity.email === displayUser.email;
}
