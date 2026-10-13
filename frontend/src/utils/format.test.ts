import { discountPercent, money, shortMoney } from './format';

describe('format', () => {
  it('formats VND with dot separators', () => {
    expect(money(1290000)).toBe('1.290.000₫');
  });
  it('shortens big amounts', () => {
    expect(shortMoney(2_500_000)).toBe('2,5 tr');
    expect(shortMoney(1_200_000_000)).toBe('1,2 tỷ');
  });
  it('computes the sale percentage', () => {
    expect(discountPercent(1_000_000, 790_000)).toBe(21);
    expect(discountPercent(1_000_000, null)).toBeNull();
  });
});
