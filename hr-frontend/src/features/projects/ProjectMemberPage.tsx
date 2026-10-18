/**
 * @component ProjectMemberPage
 * @description W10 screen — PRJ-08 project member view placeholder (D-W7, FRD-01 Wave 3).
 * Route: `/projects/:projectId/members/:code`
 * This placeholder is reachable by direct URL only — no navigation link currently points to
 * it, because the "project Team tab" that would link here is F04's own unbuilt screen.
 * F04 both adds the real content and the link when it ships.
 * @props (none — reads params from route)
 * @example
 * Route: { path: '/projects/:projectId/members/:code', element: <ProjectMemberPage /> }
 */

import type { ReactElement } from 'react'
import { useParams, Link } from 'react-router-dom'
import { Card } from '../../components/index'

/**
 * ProjectMemberPage renders a placeholder for the PRJ-08 project member view.
 *
 * Per D-W7's resolution: "build a placeholder screen now, reachable by direct URL only
 * until F04 adds the link." The route exists and is reachable; no nav item or button
 * currently points to it.
 *
 * Real content: project assignment, project reviews (type=PROJECT), client feedback —
 * all require the project/project_assignment tables (F04) and the
 * GET /api/projects/{id}/members/{code} endpoint (W11, also deferred).
 */
export function ProjectMemberPage(): ReactElement {
  const { projectId, code } = useParams<{ projectId: string; code: string }>()

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      <Link
        to="/"
        className="text-sm text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded"
      >
        ← Back to Dashboard
      </Link>

      <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
        Project member: {code ?? '…'}
      </h1>

      <Card>
        <div className="text-center py-12 space-y-3">
          <p className="text-base font-medium text-gray-500">Not available yet</p>
          <p className="text-sm text-gray-400 max-w-md mx-auto">
            The project member view (PRJ-08) is not yet available. Project assignment details,
            project reviews and client feedback will be shown here once the Projects module (F04)
            is delivered.
          </p>
          {projectId !== undefined && (
            <p className="text-xs text-gray-300">
              Project {projectId} · Member {code}
            </p>
          )}
        </div>
      </Card>
    </div>
  )
}
