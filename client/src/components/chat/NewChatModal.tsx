import { useState, useEffect, useRef } from 'react';
import { X, Search } from 'lucide-react';
import clsx from 'clsx';
import api from '@/api/axios';
import { useChatStore } from '@/stores/chatStore';
import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Input';
import type { ChatRoom, RoomType } from '@/types/chat';
import type { User } from '@/types/user';

interface NewChatModalProps {
  isOpen: boolean;
  onClose: () => void;
}

interface CreateRoomBody {
  type: RoomType;
  name?: string;
  memberIds: string[];
}

export function NewChatModal({ isOpen, onClose }: NewChatModalProps) {
  const { rooms, setRooms, setCurrentRoom } = useChatStore();

  const [type, setType] = useState<RoomType>('DIRECT');
  const [groupName, setGroupName] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<User[]>([]);
  const [selectedUsers, setSelectedUsers] = useState<User[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout>>(undefined);

  useEffect(() => {
    if (searchQuery.length < 2) {
      setSearchResults([]);
      return;
    }

    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(async () => {
      setIsSearching(true);
      try {
        const res = await api.get<User[]>('/users/search', {
          params: { keyword: searchQuery },
        });
        // Exclude already selected users
        const selectedIds = new Set(selectedUsers.map((u) => u.id));
        setSearchResults(res.data.filter((u) => !selectedIds.has(u.id)));
      } catch {
        setSearchResults([]);
      } finally {
        setIsSearching(false);
      }
    }, 300);

    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [searchQuery, selectedUsers]);

  if (!isOpen) return null;

  function handleClose() {
    setType('DIRECT');
    setGroupName('');
    setSearchQuery('');
    setSearchResults([]);
    setSelectedUsers([]);
    setError(null);
    onClose();
  }

  function selectUser(user: User) {
    if (type === 'DIRECT') {
      setSelectedUsers([user]);
    } else {
      setSelectedUsers((prev) => [...prev, user]);
    }
    setSearchQuery('');
    setSearchResults([]);
  }

  function removeUser(userId: string) {
    setSelectedUsers((prev) => prev.filter((u) => u.id !== userId));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    if (selectedUsers.length === 0) {
      setError('대화할 사용자를 검색하여 선택해주세요.');
      return;
    }

    if (type === 'GROUP' && !groupName.trim()) {
      setError('그룹 채팅방 이름을 입력해주세요.');
      return;
    }

    const memberIds = selectedUsers.map((u) => u.id);
    const body: CreateRoomBody = { type, memberIds };
    if (type === 'GROUP') body.name = groupName.trim();

    setIsSubmitting(true);
    try {
      const response = await api.post<ChatRoom>('/chats', body);
      const newRoom = response.data;
      setRooms([newRoom, ...rooms.filter((r) => r.id !== newRoom.id)]);
      setCurrentRoom(newRoom);
      handleClose();
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? '채팅방 생성에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div
        className="absolute inset-0 bg-black/40"
        onClick={handleClose}
        aria-hidden="true"
      />

      <div className="relative z-10 w-full max-w-md rounded-xl bg-white p-6 shadow-xl">
        <div className="mb-5 flex items-center justify-between">
          <h2 className="text-base font-semibold text-slate-900">새 채팅 시작</h2>
          <button
            type="button"
            onClick={handleClose}
            className="rounded-md p-1 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
          >
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div>
            <p className="mb-2 text-sm font-medium text-slate-700">채팅 유형</p>
            <div className="flex gap-2">
              {(['DIRECT', 'GROUP'] as const).map((t) => (
                <button
                  key={t}
                  type="button"
                  onClick={() => {
                    setType(t);
                    if (t === 'DIRECT' && selectedUsers.length > 1) {
                      setSelectedUsers([selectedUsers[0]]);
                    }
                  }}
                  className={clsx(
                    'flex-1 rounded-lg border py-2 text-sm font-medium transition-colors',
                    type === t
                      ? 'border-primary-600 bg-primary-50 text-primary-700'
                      : 'border-slate-300 text-slate-600 hover:bg-slate-50',
                  )}
                >
                  {t === 'DIRECT' ? '1:1 채팅' : '그룹 채팅'}
                </button>
              ))}
            </div>
          </div>

          {type === 'GROUP' && (
            <Input
              label="그룹 채팅방 이름"
              placeholder="채팅방 이름을 입력하세요"
              value={groupName}
              onChange={(e) => setGroupName(e.target.value)}
            />
          )}

          {/* Selected users */}
          {selectedUsers.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {selectedUsers.map((user) => (
                <span
                  key={user.id}
                  className="inline-flex items-center gap-1 rounded-full bg-primary-50 px-2.5 py-1 text-xs font-medium text-primary-700"
                >
                  {user.nickname}
                  <button
                    type="button"
                    onClick={() => removeUser(user.id)}
                    className="rounded-full p-0.5 hover:bg-primary-100"
                  >
                    <X size={12} />
                  </button>
                </span>
              ))}
            </div>
          )}

          {/* Search input */}
          <div className="relative">
            <div className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
              <Search size={16} />
            </div>
            <input
              type="text"
              placeholder="닉네임 또는 이메일로 검색"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full rounded-lg border border-slate-300 py-2 pl-9 pr-3 text-sm text-slate-900 placeholder:text-slate-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            />

            {/* Search results dropdown */}
            {(searchResults.length > 0 || isSearching) && searchQuery.length >= 2 && (
              <div className="absolute left-0 right-0 top-full z-20 mt-1 max-h-48 overflow-y-auto rounded-lg border border-slate-200 bg-white shadow-lg">
                {isSearching ? (
                  <div className="px-3 py-2 text-sm text-slate-400">검색 중...</div>
                ) : searchResults.length === 0 ? (
                  <div className="px-3 py-2 text-sm text-slate-400">검색 결과가 없습니다</div>
                ) : (
                  searchResults.map((user) => (
                    <button
                      key={user.id}
                      type="button"
                      onClick={() => selectUser(user)}
                      className="flex w-full items-center gap-3 px-3 py-2 text-left transition-colors hover:bg-slate-50"
                    >
                      <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary-100 text-xs font-medium text-primary-700">
                        {user.nickname.charAt(0).toUpperCase()}
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-slate-900">
                          {user.nickname}
                        </p>
                        <p className="truncate text-xs text-slate-400">{user.email}</p>
                      </div>
                    </button>
                  ))
                )}
              </div>
            )}
          </div>

          {error && <p className="text-sm text-red-500">{error}</p>}

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="secondary" onClick={handleClose}>
              취소
            </Button>
            <Button type="submit" loading={isSubmitting}>
              채팅 시작
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
