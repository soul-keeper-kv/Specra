import { z } from "zod";

/** Mirrors the bean validation on NoteRequest so the user sees errors before a round trip. */
export const noteSchema = z.object({
  title: z.string().trim().min(1, "Title is required").max(200, "Max 200 characters"),
  content: z.string().trim().min(1, "Content is required"),
  tags: z.array(z.string().trim().max(64, "Tags are limited to 64 characters")).max(20),
});

export type NoteFormValues = z.infer<typeof noteSchema>;

export const chatSchema = z.object({
  message: z.string().trim().min(1, "Type something first").max(8000),
});

export const askSchema = z.object({
  question: z.string().trim().min(1, "Ask a question").max(4000),
  topK: z.number().int().min(1).max(20).default(4),
  similarityThreshold: z.number().min(0).max(1).default(0.5),
});
