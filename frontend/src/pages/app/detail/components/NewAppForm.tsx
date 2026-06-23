import { ProForm, ProFormDependency, ProFormDigit, ProFormSelect, ProFormText, ProFormTextArea } from '@ant-design/pro-components';
import { Card, Form, Input, message } from 'antd';
import { history } from '@umijs/max';
import { useCallback, useEffect, useRef, useState } from 'react';
import { createApp, listRegistrySources, listRegistryTags } from '@/services/app';
import CustomEnvTable, { EnvItem, hasDuplicateEnvKeys } from './CustomEnvTable';
import { ReloadOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import {
  applyRegistrySource,
  detectRegistrySource,
  extractRepositoryPath,
  type RegistrySourceId,
} from '@/utils/imageRegistry';

export default function NewAppForm({ templates }: { templates: any[] }) {
  const [form] = Form.useForm();
  const [customEnv, setCustomEnv] = useState<EnvItem[]>([]);
  const [registrySources, setRegistrySources] = useState<{ label: string; value: RegistrySourceId }[]>([]);
  const [repositoryPath, setRepositoryPath] = useState('');
  const [tagOptions, setTagOptions] = useState<{ label: string; value: string }[]>([]);
  const [tagLoading, setTagLoading] = useState(false);
  const [tagFallback, setTagFallback] = useState(false);
  const [tagMessage, setTagMessage] = useState<string>();
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => {
    listRegistrySources()
      .then((sources) => {
        setRegistrySources(sources.map((s: { id: RegistrySourceId; name: string; host: string }) => ({
          label: s.host ? `${s.name} (${s.host})` : s.name,
          value: s.id,
        })));
      })
      .catch(() => {
        setRegistrySources([
          { label: 'GHCR (ghcr.io)', value: 'ghcr' },
          { label: 'Docker Hub (docker.io)', value: 'dockerhub' },
          { label: '自定义', value: 'custom' },
        ]);
      });
  }, []);

  const fetchTags = useCallback(async (image?: string, templateId?: number) => {
    if (!image?.trim()) {
      setTagOptions([]);
      setTagFallback(false);
      setTagMessage(undefined);
      return;
    }
    setTagLoading(true);
    try {
      const result = await listRegistryTags(image.trim(), templateId);
      setTagOptions((result.tags || []).map((tag: string) => ({ label: tag, value: tag })));
      setTagFallback(result.fallback);
      setTagMessage(result.fallback ? result.message : undefined);
      if (result.defaultTag) form.setFieldValue('tag', result.defaultTag);
    } catch {
      const template = templates.find((t) => t.id === templateId);
      setTagOptions([]);
      setTagFallback(true);
      setTagMessage('无法连接镜像仓库，请手动输入标签');
      if (template?.defaultTag) form.setFieldValue('tag', template.defaultTag);
    } finally {
      setTagLoading(false);
    }
  }, [form, templates]);

  const scheduleFetchTags = useCallback((image?: string, templateId?: number) => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => fetchTags(image, templateId), 500);
  }, [fetchTags]);

  useEffect(() => () => { if (debounceRef.current) clearTimeout(debounceRef.current); }, []);

  const applyImageFromSource = useCallback((source: RegistrySourceId, repoPath: string, templateId?: number) => {
    const image = applyRegistrySource(source, repoPath);
    form.setFieldsValue({ imageRegistrySource: source, image });
    setTagFallback(false);
    setTagMessage(undefined);
    fetchTags(image, templateId);
  }, [form, fetchTags]);

  const handleTemplateChange = useCallback((templateId: number) => {
    const selectedTemplate = templates.find((t) => t.id === templateId);
    if (!selectedTemplate) return;
    const repoPath = extractRepositoryPath(selectedTemplate.image);
    const source = detectRegistrySource(selectedTemplate.image);
    setRepositoryPath(repoPath);
    form.setFieldsValue({ tag: selectedTemplate.defaultTag || 'v1.0.0' });
    applyImageFromSource(source, repoPath, templateId);
  }, [templates, form, applyImageFromSource]);

  const handleRegistrySourceChange = useCallback((source: RegistrySourceId) => {
    const templateId = form.getFieldValue('templateId');
    if (source === 'custom') {
      form.setFieldsValue({ imageRegistrySource: source });
      return;
    }
    const repoPath = repositoryPath || extractRepositoryPath(form.getFieldValue('image') || '');
    setRepositoryPath(repoPath);
    applyImageFromSource(source, repoPath, templateId);
  }, [form, repositoryPath, applyImageFromSource]);

  const handleImageChange = useCallback((image: string) => {
    const templateId = form.getFieldValue('templateId');
    const source = form.getFieldValue('imageRegistrySource') as RegistrySourceId;
    const repoPath = extractRepositoryPath(image);
    setRepositoryPath(repoPath);
    if (source !== 'custom') {
      const expected = applyRegistrySource(source, repoPath);
      if (image.trim() !== expected) form.setFieldsValue({ imageRegistrySource: 'custom' });
    }
    scheduleFetchTags(image, templateId);
  }, [form, scheduleFetchTags]);

  return (
    <PageContainer title="新建应用">
      <Card>
        <ProForm
          form={form}
          onFinish={async (values) => {
            if (hasDuplicateEnvKeys(customEnv)) {
              message.error('自定义环境变量存在重复 Key');
              return;
            }
            const template = templates.find((t) => t.id === values.templateId);
            const envSchema: any[] = template?.envSchema || [];
            const schemaEnv = envSchema.map((schema: any) => ({
              key: schema.key,
              value: values[`env_${schema.key}`] ?? schema.default ?? '',
              secret: schema.secret ?? false,
            }));
            const mergedCustom = customEnv
              .filter((item) => item.key?.trim())
              .map((item) => ({ key: item.key.trim(), value: String(item.value ?? ''), secret: item.secret }));
            const created = await createApp({
              name: values.name,
              templateId: values.templateId,
              image: values.image,
              tag: values.tag,
              remark: values.remark,
              resources: { cpu: values.cpu || '1', memory: values.memory || '1Gi' },
              replicas: values.replicas || 1,
              env: [...schemaEnv, ...mergedCustom],
            });
            message.success('创建成功');
            history.push(`/app/detail/${created.id}`);
          }}
        >
          <ProFormText name="name" label="应用名称" rules={[{ required: true }]} />
          <ProFormSelect
            name="templateId"
            label="模板"
            rules={[{ required: true }]}
            options={templates.map((t) => ({ label: `${t.name} (${t.image})`, value: t.id }))}
            fieldProps={{ onChange: handleTemplateChange }}
          />
          <ProFormSelect
            name="imageRegistrySource"
            label="镜像源"
            rules={[{ required: true, message: '请选择镜像源' }]}
            initialValue="ghcr"
            options={registrySources}
            fieldProps={{ onChange: handleRegistrySourceChange }}
          />
          <ProFormDependency name={['imageRegistrySource']}>
            {({ imageRegistrySource }) => (
              <ProFormText
                name="image"
                label="镜像地址"
                rules={[{ required: true, message: '请输入镜像地址' }]}
                fieldProps={{ onChange: (e) => handleImageChange(e.target.value) }}
              />
            )}
          </ProFormDependency>
          <ProFormDependency name={['templateId', 'image']}>
            {({ templateId, image }) => (
              tagFallback ? (
                <ProFormText name="tag" label="镜像标签" rules={[{ required: true }]} extra={tagMessage} />
              ) : (
                <ProFormSelect
                  name="tag"
                  label="镜像标签"
                  rules={[{ required: true }]}
                  options={tagOptions}
                  fieldProps={{ showSearch: true, loading: tagLoading }}
                />
              )
            )}
          </ProFormDependency>
          <ProFormTextArea name="remark" label="备注" />
          <ProFormDigit name="replicas" label="副本数" initialValue={1} min={1} fieldProps={{ precision: 0 }} />
          <ProFormText name="cpu" label="CPU 限制" initialValue="1" />
          <ProFormText name="memory" label="内存限制" initialValue="1Gi" />
          <ProFormDependency name={['templateId']}>
            {({ templateId }) => {
              const selectedTemplate = templates.find((t) => t.id === templateId);
              return (selectedTemplate?.envSchema || []).map((schema: any) => (
                schema.secret ? (
                  <ProFormText.Password key={schema.key} name={`env_${schema.key}`} label={schema.label || schema.key} initialValue={schema.default} />
                ) : (
                  <ProFormText key={schema.key} name={`env_${schema.key}`} label={schema.label || schema.key} initialValue={schema.default} />
                )
              ));
            }}
          </ProFormDependency>
          <div style={{ marginTop: 8, marginBottom: 8, fontWeight: 600 }}>自定义环境变量</div>
          <CustomEnvTable value={customEnv} onChange={setCustomEnv} secretPlaceholder="请输入值" />
        </ProForm>
      </Card>
    </PageContainer>
  );
}
