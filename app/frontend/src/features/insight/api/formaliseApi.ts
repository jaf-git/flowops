import { apiRequest } from '../../../shared/api/client';

export interface FormaliseNode {
  title: string;
  detail?: string;

  steps: string[];
}

export interface Formalised {
  nodeId: string;
  taskTemplateId: string;
}

export async function formaliseNode(
  input: { nodeId: string } & FormaliseNode,
): Promise<Formalised> {
  const { nodeId, ...body } = input;
  return apiRequest<Formalised>(`/discovery/nodes/${encodeURIComponent(nodeId)}/formalise`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}
