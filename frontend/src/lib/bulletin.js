// Must match the PostCategory enum in the backend.
export const POST_CATEGORIES = [
  ['ANNOUNCEMENT', 'Announcements'],
  ['EVENT', 'Events'],
  ['SERVICE', 'Services'],
];

export const postCategoryLabel = (value) =>
  ({ ANNOUNCEMENT: 'Announcement', EVENT: 'Event', SERVICE: 'Service' })[value] ?? value;

export const formatDate = (iso) =>
  new Date(iso).toLocaleDateString('en-ZA', { year: 'numeric', month: 'short', day: 'numeric' });
