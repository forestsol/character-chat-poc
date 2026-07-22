export type BookSummary = {
  id: number; bookKey: string; title: string; author: string | null; status: string;
  createdAt: string; updatedAt: string;
};

export type CharacterReview = {
  candidateId: number; name: string; description: string; confidence: number; originSource: string;
  reviewStatus: string; recommendedRole: string | null; recommendationReason: string | null;
  mergedIntoCandidateId: number | null; characterId: number | null; narrativeRole: string | null; chatEnabled: boolean;
};

export type ProfileEvidence = {
  id: number; profileField: string; paragraphId: number | null; imageId: number | null; sourceType: string;
  inferenceType: string; description: string; confidence: number;
};

export type CharacterProfile = {
  id: number; characterId: number; storyPoint: string; roleDescription: string; appearance: string;
  personality: string; values: string; goals: string; speechStyle: string; majorExperiences: string;
  attitudesTowardOthers: string; knownFacts: string; systemPrompt: string; evidence: ProfileEvidence[];
};

export type RagParagraph = { paragraphId: number; sourceOrder: number; pageNumber: number; content: string };
export type RagRange = {
  sourceOrderStart: number; sourceOrderEnd: number; pageNumberStart: number; pageNumberEnd: number;
  score: number; paragraphs: RagParagraph[];
};
export type DirectRelation = {
  id: number; sourceName: string; sourceType: string; relationType: string; targetName: string;
  targetType: string; description: string; confidence: number; reviewStatus: string;
};
export type ChatMessage = {
  role: "USER" | "ASSISTANT";
  content: string;
  responseType?: "ANSWER" | "CLARIFICATION" | "UNKNOWN" | "SOCIAL";
};
export type ChatResponse = {
  bookId: number;
  character: { id: number; name: string; narrativeRole: string; storyPoint: string };
  answer: string;
  grounded: boolean;
  responseType: "ANSWER" | "CLARIFICATION" | "UNKNOWN" | "SOCIAL";
  debug: {
    usedParagraphIds: number[]; usedRelationIds: number[]; usedProfileEvidenceIds: number[];
    ragRanges: RagRange[]; directRelations: DirectRelation[]; profileEvidence: ProfileEvidence[];
    rewrite: {
      intent: "FACTUAL" | "SOCIAL"; attempted: boolean; resolved: boolean; fallback: boolean; standaloneQuery: string;
      ambiguousReference: string; referentCandidates: string[];
    };
  };
};

export type KnowledgeGraph = {
  events: Array<{ id: number; name: string; description: string; sequenceOrder: number; confidence: number; reviewStatus: string }>;
  entities: Array<{ id: number; entityType: string; name: string; description: string; reviewStatus: string }>;
  relations: Array<{ id: number; sourceEntityId: number; relationType: string; targetEntityId: number; description: string; confidence: number }>;
};
