/** Cart lines kept in the browser. Prices are never trusted from here: the server re-prices on every quote. */
export interface CartItem {
  variantId: number;
  quantity: number;
  slug: string;
  name: string;
  image: string;
  size: string;
  price: number;
}

export const MAX_QTY = 10;
export const MAX_LINES = 20;

export type CartAction =
  | { type: 'add'; item: CartItem }
  | { type: 'setQty'; variantId: number; quantity: number }
  | { type: 'remove'; variantId: number }
  | { type: 'clear' };

export function cartReducer(state: CartItem[], action: CartAction): CartItem[] {
  switch (action.type) {
    case 'add': {
      const existing = state.find((i) => i.variantId === action.item.variantId);
      if (existing) {
        return state.map((i) => i.variantId === action.item.variantId
          ? { ...i, quantity: Math.min(MAX_QTY, i.quantity + action.item.quantity) }
          : i);
      }
      if (state.length >= MAX_LINES) return state;
      return [...state, { ...action.item, quantity: Math.min(MAX_QTY, action.item.quantity) }];
    }
    case 'setQty':
      return state.map((i) => i.variantId === action.variantId
        ? { ...i, quantity: Math.max(1, Math.min(MAX_QTY, action.quantity)) }
        : i);
    case 'remove':
      return state.filter((i) => i.variantId !== action.variantId);
    case 'clear':
      return [];
    default:
      return state;
  }
}
