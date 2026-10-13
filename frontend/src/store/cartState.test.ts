import { cartReducer, MAX_QTY, type CartItem } from './cartState';

const item = (variantId: number, quantity = 1): CartItem => ({
  variantId, quantity, slug: 's', name: 'Giày', image: '/x.svg', size: '42', price: 1_000_000,
});

describe('cartReducer', () => {
  it('merges the same size into one line and caps the quantity', () => {
    let s = cartReducer([], { type: 'add', item: item(1, 4) });
    s = cartReducer(s, { type: 'add', item: item(1, 9) });
    expect(s).toHaveLength(1);
    expect(s[0].quantity).toBe(MAX_QTY);
  });

  it('keeps quantities between 1 and the maximum', () => {
    const s = cartReducer([item(1, 2)], { type: 'setQty', variantId: 1, quantity: 0 });
    expect(s[0].quantity).toBe(1);
  });

  it('removes and clears', () => {
    const s = cartReducer([item(1), item(2)], { type: 'remove', variantId: 1 });
    expect(s.map((i) => i.variantId)).toEqual([2]);
    expect(cartReducer(s, { type: 'clear' })).toEqual([]);
  });
});
