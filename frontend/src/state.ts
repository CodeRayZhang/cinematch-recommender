// Compatibility exports keep views small while ownership lives in focused stores.
export {
  user,
  ready,
  sessionError,
  authOpen,
  loadSession,
  requireUser,
  logout,
} from "./stores/session";
export {
  library,
  libraryRevision,
  loadLibrary,
  movieStatus,
  toggleWatchlist,
} from "./stores/library";
export { toast, notify } from "./stores/ui";
