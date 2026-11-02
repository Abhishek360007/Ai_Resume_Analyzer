import { useState } from 'react'
import { Outlet, NavLink, useNavigate, useLocation } from 'react-router-dom'
import { useDispatch, useSelector } from 'react-redux'
import { logout } from '@store/slices/authSlice'
import {
  selectUser,
  selectUserFullName,
  selectUserRole,
  selectIsAdmin,
  selectCanManageEmployees,
  selectCanUploadResumes,
  selectCanManageJobs,
} from '@store/selectors/authSelectors'
import { useTrackerEventStream } from '@/hooks/useTrackerEventStream'
import ProcessingBanner from '@/components/ProcessingBanner/ProcessingBanner'
import BrandMark from '@/components/BrandMark/BrandMark'
import {
  LayoutDashboard,
  ShieldCheck,
  Users,
  Building2,
  Upload,
  ClipboardCheck,
  UserCircle,
  Briefcase,
  Target,
  Wrench,
  LogOut,
  ChevronLeft,
  ChevronRight,
  Menu,
} from 'lucide-react'
import styles from './Layout.module.css'

const SIDEBAR_KEY = 'sidebarCollapsed'

const PAGE_TITLES: Record<string, string> = {
  '': 'Dashboard',
  admin: 'Admin Dashboard',
  users: 'User Management',
  employees: 'Employees',
  upload: 'Upload Resumes',
  confirmations: 'Confirmations',
  candidates: 'Candidates',
  jobs: 'Job Requirements',
  matching: 'Candidate Matching',
  skills: 'Skills Master',
}

const formatRole = (role?: string | null) =>
  role ? role.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase()) : ''

const initials = (name?: string | null) =>
  (name || 'U')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('')

const Layout = () => {
  const dispatch = useDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const user = useSelector(selectUser)
  const fullName = useSelector(selectUserFullName)
  const userRole = useSelector(selectUserRole)
  const isAdmin = useSelector(selectIsAdmin)
  const canManageEmployees = useSelector(selectCanManageEmployees)
  const canUploadResumes = useSelector(selectCanUploadResumes)
  const canManageJobs = useSelector(selectCanManageJobs)

  // Live processing-status stream — runs for the app's lifetime so updates keep
  // flowing into Redux (and the banner below) regardless of the active route.
  useTrackerEventStream()

  const [collapsed, setCollapsed] = useState<boolean>(() => {
    try {
      return localStorage.getItem(SIDEBAR_KEY) === 'true'
    } catch {
      return false
    }
  })

  const toggleSidebar = () => {
    setCollapsed((prev) => {
      const next = !prev
      try { localStorage.setItem(SIDEBAR_KEY, String(next)) } catch { /* noop */ }
      return next
    })
  }

  const handleLogout = () => {
    dispatch(logout())
    navigate('/login')
  }

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    isActive
      ? `${styles.navItem} ${styles.navItemActive}`
      : styles.navItem

  const pageTitle = PAGE_TITLES[location.pathname.split('/')[1] ?? ''] ?? ''
  const roleLabel = formatRole(userRole)
  const hasAdminSection = isAdmin || canManageEmployees

  const sectionLabel = (label: string) =>
    collapsed
      ? <div className={styles.navDivider} aria-hidden="true" />
      : <div className={styles.navSection}>{label}</div>

  return (
    <div className={`${styles.layout} ${collapsed ? styles.layoutCollapsed : ''}`}>

      {/* ── Sidebar ─────────────────────────────────────────────── */}
      <aside className={`${styles.sidebar} ${collapsed ? styles.sidebarCollapsed : ''}`}>

        <div className={styles.sidebarHeader}>
          <div className={styles.brand}>
            <BrandMark size={34} className={styles.brandMark} />
            {!collapsed && (
              <div className={styles.brandText}>
                <span className={styles.brandName}>AI Resume Analyzer</span>
                <span className={styles.brandTagline}>Talent intelligence</span>
              </div>
            )}
          </div>
          <button
            className={styles.toggleBtn}
            onClick={toggleSidebar}
            title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {collapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
          </button>
        </div>

        <nav className={styles.nav}>
          {sectionLabel('Overview')}
          <NavLink to="/" className={navLinkClass} end title={collapsed ? 'Dashboard' : undefined}>
            <LayoutDashboard size={18} className={styles.navIcon} />
            {!collapsed && <span>Dashboard</span>}
          </NavLink>

          {isAdmin && (
            <NavLink to="/admin" className={navLinkClass} title={collapsed ? 'Admin Dashboard' : undefined}>
              <ShieldCheck size={18} className={styles.navIcon} />
              {!collapsed && <span>Admin Dashboard</span>}
            </NavLink>
          )}

          {sectionLabel('Recruiting')}
          {canUploadResumes && (
            <NavLink to="/upload" className={navLinkClass} title={collapsed ? 'Upload Resumes' : undefined}>
              <Upload size={18} className={styles.navIcon} />
              {!collapsed && <span>Upload Resumes</span>}
            </NavLink>
          )}

          {canUploadResumes && (
            <NavLink to="/confirmations" className={navLinkClass} title={collapsed ? 'Confirmations' : undefined}>
              <ClipboardCheck size={18} className={styles.navIcon} />
              {!collapsed && <span>Confirmations</span>}
            </NavLink>
          )}

          <NavLink to="/candidates" className={navLinkClass} title={collapsed ? 'Candidates' : undefined}>
            <UserCircle size={18} className={styles.navIcon} />
            {!collapsed && <span>Candidates</span>}
          </NavLink>

          {canManageJobs && (
            <NavLink to="/jobs" className={navLinkClass} title={collapsed ? 'Job Requirements' : undefined}>
              <Briefcase size={18} className={styles.navIcon} />
              {!collapsed && <span>Job Requirements</span>}
            </NavLink>
          )}

          <NavLink to="/matching" className={navLinkClass} title={collapsed ? 'Candidate Matching' : undefined}>
            <Target size={18} className={styles.navIcon} />
            {!collapsed && <span>Candidate Matching</span>}
          </NavLink>

          {hasAdminSection && sectionLabel('Administration')}
          {isAdmin && (
            <NavLink to="/users" className={navLinkClass} title={collapsed ? 'User Management' : undefined}>
              <Users size={18} className={styles.navIcon} />
              {!collapsed && <span>User Management</span>}
            </NavLink>
          )}

          {canManageEmployees && (
            <NavLink to="/employees" className={navLinkClass} title={collapsed ? 'Employee Management' : undefined}>
              <Building2 size={18} className={styles.navIcon} />
              {!collapsed && <span>Employees</span>}
            </NavLink>
          )}

          {isAdmin && (
            <NavLink to="/skills" className={navLinkClass} title={collapsed ? 'Skills Master' : undefined}>
              <Wrench size={18} className={styles.navIcon} />
              {!collapsed && <span>Skills Master</span>}
            </NavLink>
          )}
        </nav>

        <div className={styles.sidebarFooter}>
          {user && (
            <div className={`${styles.userInfo} ${collapsed ? styles.userInfoCollapsed : ''}`}>
              <div className={styles.userAvatar} title={collapsed ? fullName || undefined : undefined}>
                {initials(fullName)}
              </div>
              {!collapsed && (
                <div className={styles.userDetails}>
                  <span className={styles.userName}>{fullName}</span>
                  <span className={styles.userRole}>{roleLabel}</span>
                </div>
              )}
            </div>
          )}
          <button
            onClick={handleLogout}
            className={styles.logoutBtn}
            title={collapsed ? 'Logout' : undefined}
            aria-label="Logout"
          >
            <LogOut size={18} className={styles.navIcon} />
            {!collapsed && <span>Logout</span>}
          </button>
        </div>
      </aside>

      {/* ── Main area ────────────────────────────────────────────── */}
      <div className={styles.mainWrapper}>
        <header className={styles.topBar}>
          <button
            className={styles.mobileToggle}
            onClick={toggleSidebar}
            aria-label="Toggle sidebar"
          >
            <Menu size={20} />
          </button>
          <div className={styles.breadcrumb}>
            <span className={styles.breadcrumbRoot}>Workspace</span>
            {pageTitle && (
              <>
                <span className={styles.breadcrumbSep}>/</span>
                <span className={styles.breadcrumbCurrent}>{pageTitle}</span>
              </>
            )}
          </div>
          {user && (
            <div className={styles.topBarRight}>
              <div className={styles.topBarIdentity}>
                <span className={styles.topBarUser}>{fullName}</span>
                <span className={styles.topBarRole}>{roleLabel}</span>
              </div>
              <div className={styles.topBarAvatar} aria-hidden="true">{initials(fullName)}</div>
            </div>
          )}
        </header>

        {canUploadResumes && <ProcessingBanner />}

        <main className={styles.main}>
          <Outlet />
        </main>

        <footer className={styles.footer}>
          <p>AI Resume Analyzer — Built by Abhishek Raj © 2026</p>
        </footer>
      </div>
    </div>
  )
}

export default Layout
