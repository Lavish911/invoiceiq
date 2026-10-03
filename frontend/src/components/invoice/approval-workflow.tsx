'use client';

import { useState } from 'react';
import { useWorkflow, useApprove, useReject } from '@/hooks/use-workflow';

import { Spinner } from '@/components/ui/spinner';
import { CheckCircle2, XCircle, Clock, AlertTriangle, ShieldCheck, RefreshCw } from 'lucide-react';
import { cn } from '@/lib/utils';
import { isAxiosError } from 'axios';

function getActionErrorMessage(err: unknown): string {
  if (isAxiosError(err)) {
    const status = err.response?.status;
    if (status === 409) return 'Conflict: The workflow state has changed since you loaded it. Please refresh and try again.';
    if (status === 403) return 'You do not have permission to perform this approval action.';
    if (status === 404) return 'Workflow not found. It may have been deleted or not yet initiated.';
    if (status === 401) return 'Your session has expired. Please log in again.';
    if (!err.response) return 'Network error. Please check your connection and try again.';
    return err.response.data?.message || 'Failed to process approval action.';
  }
  return 'An unexpected error occurred.';
}

interface ApprovalWorkflowProps {
  invoiceId: string;
}

export function ApprovalWorkflow({ invoiceId }: ApprovalWorkflowProps) {
  const { data: workflow, isLoading, isError, error, refetch, isFetching } = useWorkflow(invoiceId);
  const approveMutation = useApprove(invoiceId);
  const rejectMutation = useReject(invoiceId);

  const [comment, setComment] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);


  // 404 or 400 means no workflow started yet (backend returns 400 when no workflow exists)
  if (isError) {
    if (isAxiosError(error) && (error.response?.status === 404 || error.response?.status === 400)) {
      return (
        <div className="rounded-lg bg-gray-50 border border-gray-200 p-6 text-center">
          <ShieldCheck className="mx-auto h-8 w-8 text-gray-400" aria-hidden="true" />
          <h3 className="mt-2 text-sm font-medium text-gray-900">No Approval Workflow</h3>
          <p className="mt-1 text-sm text-gray-500">No approval workflow has been initiated for this invoice.</p>
        </div>
      );
    }
    return (
      <div className="rounded-lg bg-red-50 p-4 border border-red-200" role="alert">
        <h3 className="text-sm font-medium text-red-800">Error loading workflow</h3>
        <p className="mt-1 text-sm text-red-700">{error instanceof Error ? error.message : 'Unknown error'}</p>
        <button
          onClick={() => refetch()}
          className="mt-2 inline-flex items-center gap-1 text-sm font-medium text-red-600 hover:text-red-500 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600"
        >
          <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
          Retry
        </button>
      </div>
    );
  }

  if (isLoading || !workflow) {
    return (
      <div className="flex justify-center p-8 border rounded-lg bg-white shadow-sm">
        <Spinner size="md" />
      </div>
    );
  }

  const currentStep = workflow.approvalSteps.find((s) => s.stepNumber === workflow.currentStep);
  const canAct =
    workflow.status === 'PENDING_APPROVAL' &&
    currentStep &&
    currentStep.status === 'PENDING';

  const handleAction = (type: 'APPROVE' | 'REJECT') => {
    setActionError(null);
    const request = { comment, expectedVersion: workflow.version };
    
    const mutation = type === 'APPROVE' ? approveMutation : rejectMutation;
    
    mutation.mutate(request, {
      onSuccess: () => {
        setComment('');
      },
      onError: (err) => {
        setActionError(getActionErrorMessage(err));
      }
    });
  };

  const isPendingAction = approveMutation.isPending || rejectMutation.isPending;
  const isConflictError = actionError?.includes('Conflict');

  return (
    <div className="rounded-lg bg-white shadow-sm ring-1 ring-gray-200">
      <div className="border-b border-gray-200 px-4 py-4 sm:px-6 flex flex-wrap justify-between items-center gap-2">
        <h3 className="text-base font-semibold leading-6 text-gray-900 flex items-center gap-2">
          <ShieldCheck className="h-5 w-5 text-indigo-600" aria-hidden="true" />
          Approval Workflow
        </h3>
        <div className="flex items-center gap-2">
          <button
            onClick={() => refetch()}
            disabled={isFetching}
            aria-label="Refresh workflow status"
            className="p-1.5 text-gray-400 hover:text-gray-600 rounded-md hover:bg-gray-100 disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
          >
            <RefreshCw className={cn('h-4 w-4', isFetching && 'animate-spin')} aria-hidden="true" />
          </button>
          <span
            className={cn(
              'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
              workflow.status === 'APPROVED' ? 'bg-green-100 text-green-800' :
              workflow.status === 'REJECTED' ? 'bg-red-100 text-red-800' :
              'bg-yellow-100 text-yellow-800'
            )}
          >
            {workflow.status.replace('_', ' ')}
          </span>
        </div>
      </div>

      <div className="px-4 py-5 sm:p-6 space-y-8">
        {/* Step Timeline */}
        <div className="flow-root">
          <ul role="list" className="-mb-8">
            {workflow.approvalSteps.map((step, stepIdx) => {
              const isLast = stepIdx === workflow.approvalSteps.length - 1;
              const isCurrent = step.stepNumber === workflow.currentStep && workflow.status === 'PENDING_APPROVAL';

              return (
                <li key={step.id}>
                  <div className="relative pb-8">
                    {!isLast ? (
                      <span className="absolute left-4 top-4 -ml-px h-full w-0.5 bg-gray-200" aria-hidden="true" />
                    ) : null}
                    <div className="relative flex space-x-3">
                      <div>
                        <span
                          className={cn(
                            'h-8 w-8 rounded-full flex items-center justify-center ring-8 ring-white',
                            step.status === 'APPROVED' ? 'bg-green-500' :
                            step.status === 'REJECTED' ? 'bg-red-500' :
                            isCurrent ? 'bg-indigo-500' : 'bg-gray-300'
                          )}
                          aria-label={`Step ${step.stepNumber}: ${step.status}`}
                        >
                          {step.status === 'APPROVED' ? (
                            <CheckCircle2 className="h-5 w-5 text-white" aria-hidden="true" />
                          ) : step.status === 'REJECTED' ? (
                            <XCircle className="h-5 w-5 text-white" aria-hidden="true" />
                          ) : (
                            <Clock className="h-5 w-5 text-white" aria-hidden="true" />
                          )}
                        </span>
                      </div>
                      <div className="flex min-w-0 flex-1 flex-col sm:flex-row sm:justify-between sm:space-x-4 pt-1.5">
                        <div>
                          <p className="text-sm font-medium text-gray-900">
                            Step {step.stepNumber}: {step.approverRole}
                          </p>
                          {step.comment && (
                            <p className="mt-1 text-sm text-gray-500 italic">&quot;{step.comment}&quot;</p>
                          )}
                        </div>
                        <div className="whitespace-nowrap text-sm text-gray-500 mt-1 sm:mt-0 sm:text-right">
                          {step.status !== 'PENDING' && step.actedAt ? (
                            <time dateTime={step.actedAt}>{new Date(step.actedAt).toLocaleString()}</time>
                          ) : (
                            isCurrent && <span className="text-indigo-600 font-medium">Pending Action</span>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>

        {/* Action Panel */}
        {canAct && (
          <div className="rounded-lg bg-gray-50 border border-gray-200 p-4">
            <h4 className="text-sm font-medium text-gray-900 mb-3">Your Action Required</h4>
            
            {actionError && (
              <div className="mb-4 rounded-md bg-red-50 p-3 flex items-start gap-3" role="alert" aria-live="assertive">
                <AlertTriangle className="h-5 w-5 text-red-400 mt-0.5 shrink-0" aria-hidden="true" />
                <div className="flex-1">
                  <p className="text-sm text-red-800">{actionError}</p>
                  {isConflictError && (
                    <button
                      onClick={() => { setActionError(null); refetch(); }}
                      className="mt-2 text-sm font-medium text-red-600 hover:text-red-500 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600"
                    >
                      Refresh Workflow
                    </button>
                  )}
                </div>
              </div>
            )}

            <div className="space-y-4">
              <div>
                <label htmlFor="approval-comment" className="block text-sm font-medium leading-6 text-gray-900">
                  Comment (Optional)
                </label>
                <div className="mt-2">
                  <textarea
                    id="approval-comment"
                    name="comment"
                    rows={3}
                    className="block w-full rounded-md border-0 py-1.5 px-3 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                    placeholder="Provide a reason for approval or rejection…"
                    value={comment}
                    onChange={(e) => setComment(e.target.value)}
                    disabled={isPendingAction}
                  />
                </div>
              </div>
              <div className="flex flex-col sm:flex-row gap-3">
                <button
                  type="button"
                  onClick={() => handleAction('APPROVE')}
                  disabled={isPendingAction}
                  aria-busy={approveMutation.isPending}
                  className="inline-flex items-center justify-center gap-2 rounded-md bg-green-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-green-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {approveMutation.isPending && <Spinner size="sm" />}
                  Approve
                </button>
                <button
                  type="button"
                  onClick={() => handleAction('REJECT')}
                  disabled={isPendingAction}
                  aria-busy={rejectMutation.isPending}
                  className="inline-flex items-center justify-center gap-2 rounded-md bg-red-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-red-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {rejectMutation.isPending && <Spinner size="sm" />}
                  Reject
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
