import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Col, Empty, Result, Row, Space, Spin, Tag, Typography, message } from 'antd';
import { history, useAccess } from '@umijs/max';
import { useEffect, useState } from 'react';
import { getKanbanBoard, listKanbanBoards, moveKanbanTask, syncKanbanBoard } from '@/services/kanban';
import type { TaskBoardDto } from '@/services/types/app-management';

export default () => {
  const access = useAccess();
  const [board, setBoard] = useState<TaskBoardDto>();
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string>();

  const load = async () => {
    setLoading(true);
    setLoadError(undefined);
    try {
      const boards = await listKanbanBoards();
      if (boards?.length) {
        setBoard(await getKanbanBoard(boards[0].id));
      } else {
        setBoard(undefined);
      }
    } catch (e: any) {
      setLoadError(e?.message || '加载失败');
      setBoard(undefined);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleMove = async (taskId: number, columnId: number) => {
    try {
      await moveKanbanTask(taskId, columnId);
      message.success('任务已移动');
      await load();
    } catch (e: any) {
      message.error(e?.message || '移动失败');
    }
  };

  const handleSync = async () => {
    if (!board?.id) return;
    try {
      await syncKanbanBoard(board.id);
      message.success('已同步应用状态');
      await load();
    } catch (e: any) {
      message.error(e?.message || '同步失败');
    }
  };

  if (loading && !board) {
    return (
      <PageContainer title="应用运维 Kanban">
        <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>
      </PageContainer>
    );
  }

  if (loadError) {
    return (
      <PageContainer title="应用运维 Kanban">
        <Result status="error" title="加载失败" subTitle={loadError} extra={<Button onClick={load}>重试</Button>} />
      </PageContainer>
    );
  }

  return (
    <PageContainer
      title="应用运维 Kanban"
      subTitle="按应用状态可视化运维工作流，点击列名移动任务"
      extra={[
        access.canWriteApp && (
          <Button key="sync" loading={loading} onClick={handleSync}>同步应用状态</Button>
        ),
      ].filter(Boolean)}
    >
      {!board?.columns?.length ? (
        <Empty description="暂无看板数据" />
      ) : (
        <Row gutter={16} wrap={false} style={{ overflowX: 'auto' }}>
          {(board.columns || []).map((col: any) => (
            <Col key={col.id} flex="280px">
              <Card
                title={<>{col.name} <Tag>{col.tasks?.length || 0}</Tag></>}
                size="small"
                style={{ minHeight: 420, background: '#fafafa' }}
              >
                {(col.tasks || []).map((task: any) => (
                  <Card
                    key={task.id}
                    size="small"
                    style={{ marginBottom: 8, cursor: 'pointer' }}
                    onClick={() => task.applicationId && history.push(`/app/detail/${task.applicationId}`)}
                  >
                    <Typography.Text strong>{task.title}</Typography.Text>
                    <div>
                      {task.templateCode && <Tag>{task.templateCode}</Tag>}
                      {task.appStatus && <Tag color={task.appStatus === 'running' ? 'green' : 'default'}>{task.appStatus}</Tag>}
                    </div>
                    {task.applicationId && (
                      <Space size={4} style={{ marginTop: 8 }} onClick={(e) => e.stopPropagation()}>
                        {access.canAccessFiles && (
                          <Button
                            size="small"
                            type="link"
                            onClick={() => history.push(`/files?appId=${task.applicationId}`)}
                          >
                            文件
                          </Button>
                        )}
                        {access.canViewMemory && (
                          <Button
                            size="small"
                            type="link"
                            onClick={() => history.push(`/app/detail/${task.applicationId}?tab=memory`)}
                          >
                            记忆
                          </Button>
                        )}
                      </Space>
                    )}
                    {access.canWriteApp && (
                      <div style={{ marginTop: 8 }}>
                        {(board.columns || []).filter((c: any) => c.id !== col.id).map((target: any) => (
                          <Button
                            key={target.id}
                            size="small"
                            type="link"
                            onClick={(e) => {
                              e.stopPropagation();
                              handleMove(task.id, target.id);
                            }}
                          >
                            → {target.name}
                          </Button>
                        ))}
                      </div>
                    )}
                  </Card>
                ))}
              </Card>
            </Col>
          ))}
        </Row>
      )}
    </PageContainer>
  );
};
