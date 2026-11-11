"use client";

import { FormEvent, useRef, useState } from "react";
import { Bot, Copy, Send, Sparkles, User, LoaderCircle, Check } from "lucide-react";

type Message = { role: "user" | "assistant"; content: string };

export function MahiAiAssistant() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const question = draft.trim();
    if (!question || busy) return;
    const history: Message[] = [
      ...messages.filter((item) => item.content.trim()),
      { role: "user", content: question },
    ];
    setMessages([...history, { role: "assistant", content: "" }]);
    setDraft("");
    setBusy(true);
    setError("");
    setStatus("Connecting to MahiCloud AI…");
    let answer = "";

    try {
      const response = await fetch("/api/ai", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ messages: history, think_mode: false }),
      });
      if (!response.ok) {
        let detail = "The AI request could not be completed.";
        try {
          const payload = await response.json();
          if (typeof payload?.error === "string") detail = payload.error;
        } catch { /* Keep the safe fallback message. */ }
        throw new Error(detail);
      }
      if (!response.body) throw new Error("This browser does not support streaming AI responses.");

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      const applyBlock = (block: string) => {
        const data = block.split("\n")
          .filter((line) => line.startsWith("data:"))
          .map((line) => line.slice(5).trim());
        if (!data.length) return;
        let payload: Record<string, unknown>;
        try { payload = JSON.parse(data.join("\n")) as Record<string, unknown>; }
        catch { return; }
        if (payload.type === "delta" && typeof payload.text === "string") {
          answer += payload.text;
          setMessages([...history, { role: "assistant", content: answer }]);
          setStatus("");
        } else if (payload.type === "reset") {
          answer = "";
          setMessages([...history, { role: "assistant", content: "" }]);
        } else if (payload.type === "status" && typeof payload.message === "string") {
          setStatus(payload.message);
        } else if (payload.type === "error" && typeof payload.message === "string") {
          throw new Error(payload.message);
        }
      };

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true }).replace(/\r/g, "");
        let boundary = buffer.indexOf("\n\n");
        while (boundary >= 0) {
          const block = buffer.slice(0, boundary);
          buffer = buffer.slice(boundary + 2);
          applyBlock(block);
          boundary = buffer.indexOf("\n\n");
        }
      }
      buffer += decoder.decode();
      if (buffer.trim()) applyBlock(buffer);
      if (!answer.trim()) {
        setMessages([...history, { role: "assistant", content: "No text response was returned. Please try again." }]);
      }
    } catch (caught) {
      const message = caught instanceof Error ? caught.message : "The AI request failed.";
      setError(message);
      setMessages([...history, { role: "assistant", content: "I couldn't complete that request. Check the connection and try again." }]);
    } finally {
      setBusy(false);
      setStatus("");
      window.setTimeout(() => scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" }), 0);
    }
  }

  const lastAssistant = [...messages].reverse().find((item) => item.role === "assistant" && item.content.trim())?.content || "";
  async function copyAnswer() {
    if (!lastAssistant) return;
    try {
      await navigator.clipboard.writeText(lastAssistant);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1400);
    } catch {
      setError("Clipboard access was denied by the browser.");
    }
  }

  return (
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_280px]">
      <section className="panel min-h-[68vh] flex flex-col overflow-hidden">
        <div className="flex items-center justify-between border-b border-[#252830] px-5 py-4">
          <div className="flex items-center gap-3">
            <div className="grid h-10 w-10 place-items-center rounded-xl bg-red-500/15 text-red-300"><Sparkles size={19} /></div>
            <div><h2 className="text-sm font-semibold">MahiCloud AI</h2><p className="muted mt-1 text-[11px]">Owner-only repair recipe drafting</p></div>
          </div>
          <button type="button" onClick={copyAnswer} disabled={!lastAssistant} className="btn btn-secondary text-xs">
            {copied ? <Check size={14} /> : <Copy size={14} />} {copied ? "Copied" : "Copy answer"}
          </button>
        </div>

        <div ref={scrollRef} role="log" aria-live="polite" className="flex-1 space-y-4 overflow-y-auto p-4 sm:p-6">
          {!messages.length && (
            <div className="mx-auto flex max-w-xl flex-col items-center py-14 text-center">
              <div className="grid h-14 w-14 place-items-center rounded-2xl border border-red-500/20 bg-red-500/10 text-red-300"><Bot size={27} /></div>
              <h3 className="mt-5 text-lg font-semibold">Build a safer repair recipe</h3>
              <p className="muted mt-2 text-sm leading-6">Describe the Android symptom, affected app or network issue. The assistant can draft a v1 JSON recipe using only the native allowlisted actions.</p>
              <div className="mt-5 flex flex-wrap justify-center gap-2">
                {["Create a read-only Wi-Fi diagnostic recipe", "Draft a Play Store troubleshooting guide", "Explain why a recipe fails schema validation"].map((example) => (
                  <button type="button" key={example} onClick={() => setDraft(example)} className="rounded-full border border-[#30343d] px-3 py-2 text-xs text-gray-300 hover:border-red-500/50">{example}</button>
                ))}
              </div>
            </div>
          )}

          {messages.map((message, index) => (
            <div key={index} className={"flex gap-3 " + (message.role === "user" ? "justify-end" : "justify-start")}>
              {message.role === "assistant" && <div className="mt-1 grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-red-500/15 text-red-300"><Bot size={16} /></div>}
              <div className={"max-w-[min(90%,820px)] whitespace-pre-wrap break-words rounded-2xl px-4 py-3 text-sm leading-6 " + (message.role === "user" ? "bg-[#262a32] text-white" : "border border-[#292d36] bg-[#11141a] text-gray-200")}>
                {message.content || (busy && index === messages.length - 1 ? <span className="muted inline-flex items-center gap-2"><LoaderCircle size={14} className="animate-spin" /> Thinking…</span> : "")}
              </div>
              {message.role === "user" && <div className="mt-1 grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-[#252830] text-gray-300"><User size={16} /></div>}
            </div>
          ))}
          {status && <p className="muted pl-11 text-xs">{status}</p>}
          {error && <p role="alert" className="rounded-lg border border-red-900/50 bg-red-950/20 p-3 text-xs text-red-300">{error}</p>}
        </div>

        <form onSubmit={submit} className="border-t border-[#252830] p-4">
          <label htmlFor="mahi-ai-prompt" className="sr-only">Describe the repair recipe you need</label>
          <div className="flex items-end gap-2 rounded-2xl border border-[#343944] bg-[#0b0d11] p-2 focus-within:border-red-500/60">
            <textarea
              id="mahi-ai-prompt"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={(event) => { if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); event.currentTarget.form?.requestSubmit(); } }}
              placeholder="Describe a problem or ask for a recipe draft…"
              rows={3}
              maxLength={5000}
              disabled={busy}
              className="max-h-40 min-h-[76px] flex-1 resize-y bg-transparent px-3 py-2 text-sm text-white outline-none placeholder:text-gray-600"
            />
            <button type="submit" disabled={busy || !draft.trim()} aria-label="Send message" className="btn btn-primary mb-1 h-10 w-10 justify-center p-0">
              {busy ? <LoaderCircle size={16} className="animate-spin" /> : <Send size={16} />}
            </button>
          </div>
          <p className="muted mt-2 text-[10px]">Drafting only. The assistant cannot publish tools or execute repairs. Review every recipe in Test Lab before publishing.</p>
        </form>
      </section>

      <aside className="panel h-fit p-5">
        <div className="flex items-center gap-2 text-sm font-semibold"><span className="text-emerald-300">✓</span> Safety & connection</div>
        <ul className="muted mt-4 list-disc space-y-3 pl-4 text-xs leading-5">
          <li>Uses the existing MahiCloud Supabase AI endpoint and its configured provider routing.</li>
          <li>Provider API keys stay in Supabase Edge Function secrets; this Admin app receives no provider key.</li>
          <li>Calls are owner-gated by the Mahi Repair Supabase profile and use temporary chats.</li>
          <li>AI output is an untrusted draft. Recipe schema and action allowlists remain the authority.</li>
          <li>No AI response can directly publish a recipe or run a command on a device.</li>
        </ul>
      </aside>
    </div>
  );
}
