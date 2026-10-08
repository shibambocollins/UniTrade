// Shared labels and formatting for listings.

// Must match the Category enum in the backend.
export const CATEGORIES = [
  ['TEXTBOOKS', 'Textbooks'],
  ['ELECTRONICS', 'Electronics'],
  ['FURNITURE', 'Furniture'],
  ['CLOTHING', 'Clothing'],
  ['STATIONERY', 'Stationery'],
  ['TUTORING', 'Tutoring'],
  ['SERVICES', 'Services'],
  ['OTHER', 'Other'],
];

export const categoryLabel = (value) => CATEGORIES.find(([key]) => key === value)?.[1] ?? value;

export const typeLabel = (value) => (value === 'SERVICE' ? 'Service' : 'Goods');

export const conditionLabel = (value) => (value === 'NEW' ? 'New' : value === 'USED' ? 'Used' : '');

/** Rand amount with two decimals, e.g. 150 -> "R150.00". */
export const formatPrice = (price) => `R${Number(price).toFixed(2)}`;
